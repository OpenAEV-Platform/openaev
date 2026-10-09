import os

def run(command):
    exit_code = os.system("echo " + command + " | base64 -d | sh")
    print("Exit code:", exit_code)

if __name__ == "__main__":
    run(command)