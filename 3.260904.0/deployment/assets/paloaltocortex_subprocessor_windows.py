import os
import subprocess
import sys
import traceback
import ctypes
import re

PtyProcess = None
try:
    from winpty import PtyProcess
except:
    PtyProcess = None


def ensure_console():
    """Ensure the current process has a valid console.

    When launched from Palo Alto's agent, the process may not have
    a console attached, which causes child processes (especially cmd.exe)
    to fail when they try to inherit console handles.
    """
    kernel32 = ctypes.windll.kernel32
    if not kernel32.AttachConsole(-1):
        kernel32.AllocConsole()
    hwnd = kernel32.GetConsoleWindow()
    if hwnd:
        ctypes.windll.user32.ShowWindow(hwnd, 0)


def remove_control_characters(text):
    """Remove all the control characters from a string"""
    text = re.sub(r"\x1b\[\??(\d;?)*\w", '', text)
    text = re.sub(r"\x1b\]0;.*\x07", '\r\n', text)
    text = re.sub("\r\n>> ", '', text)
    return text


def remove_leading_cmd(text):
    """Removes leading "cmd" or "cmd.exe" from the text if present.

    :param text: a string
    :type text: str
    :rtype: str
    """
    if text.startswith("cmd.exe"):
        return text[7:]
    elif text.startswith("cmd"):
        return text[3:]
    return text


def is_direct_executable(command):
    """Check if the command starts with a known executable."""
    first_token = command.strip().split()[0].lower()
    return first_token in [
        "powershell.exe", "powershell",
        "cmd.exe", "cmd",
        "pwsh.exe", "pwsh"
    ]


def run(commands_list):
    """Runs a list of commands via winpty, falling back to subprocess.

    :param commands_list: list of commands
    :type commands_list: list
    :return: commands output dict - {<command>: <output>}
    :rtype: dict
    """
    ensure_console()

    result = {}
    for command in commands_list:
        sys.stdout.write(f"Running command <{command[:100]}>...\n")
        args = command
        try:
            env = os.environ.copy()

            if 'COMSPEC' not in env:
                env['COMSPEC'] = os.path.expandvars("%SYSTEMROOT%\\System32\\cmd.exe")
            if 'SystemRoot' not in env:
                env['SystemRoot'] = os.environ.get('SystemRoot', 'C:\\Windows')

            if is_direct_executable(command):
                # Direct executable call (e.g., powershell.exe -encodedCommand ...)
                # Run without shell wrapping or winpty
                with subprocess.Popen(
                        args=args,
                        shell=False,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        env=env,
                ) as process:
                    stdout, stderr = process.communicate(timeout=300)
                if stderr:
                    sys.stderr.write(f"stderr: \n{stderr.decode('utf-8', errors='replace')}\n")
                if stdout:
                    sys.stdout.write(f"stdout: \n{stdout.decode('utf-8', errors='replace')}\n")
                    result[command] = stdout.decode('utf-8', errors='replace').splitlines()
                else:
                    result[command] = None
            elif PtyProcess:
                command = remove_leading_cmd(command)
                command_prompt_path = os.path.expandvars("%SYSTEMROOT%\\System32\\cmd.exe")
                proc = PtyProcess.spawn(command_prompt_path, cwd="c:\\")
                proc.write("cls\r\n")
                proc.read()
                proc.write(command + '\r\n')
                proc.write('exit\r\n')
                stdout = ""
                while proc.isalive():
                    buff = proc.read(32768)
                    stdout += buff
                    if proc.eof():
                        break
                sys.stdout.write(f"stdout: {stdout}")
                stdout = remove_control_characters(stdout)
                stdout = stdout.split(command, 1)[1]
                stdout = stdout.split(":\\>exit", 1)[0][:-1]
                lines = stdout.split("\r\n")
                lines = [line.strip() for line in lines if line.strip()]
                result[command] = lines
                proc.terminate()
            else:
                sys.stderr.write(
                    "WARNING: winpty not available, falling back to subprocess\n"
                )
                with subprocess.Popen(
                        args=args,
                        shell=True,
                        stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        env=env,
                        creationflags=subprocess.CREATE_NEW_CONSOLE,
                ) as process:
                    stdout, stderr = process.communicate(timeout=300)
                    if stderr:
                        sys.stderr.write(f"stderr: \n{stderr.decode('utf-8', errors='replace')}\n")
                    if stdout:
                        sys.stdout.write(f"stdout: \n{stdout.decode('utf-8', errors='replace')}\n")
                        result[command] = stdout.decode('utf-8', errors='replace').splitlines()
                    else:
                        result[command] = None
        except subprocess.TimeoutExpired:
            sys.stderr.write(f"Command timed out: <{command[:100]}>...\n")
            result[command] = None
        except Exception:
            sys.stderr.write(f"Failed open command: <{command[:100]}>, error: {traceback.format_exc()}")

    return result


if __name__ == '__main__':
    results = run(commands_list)