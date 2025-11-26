package io.openaev.rest.domain.enums;

import lombok.Getter;

import java.util.List;

@Getter
public enum DomainKeyWords {
    ENDPOINT(List.of("lsass", "registry", "dll injection", "kernel", "winlogon", "scheduled task", "wmi", "powershell", "process injection", "credential dumping", "rootkit", "startup folder", "bits jobs", "removable media", "hardware additions", "browser extension", "firmware", "bootkit", "master boot record", "clipboard", "screen capture", "audio capture", "video capture", "disk wipe", "ransomware", "debugger evasion", "sandbox evasion", "reflective code", "access token", "system binary proxy")),
    NETWORK(List.of("lateral movement", "packet sniff", "port scan", "man-in-the-middle", "arp spoof", "smb", "rdp", "dns tunnel", "network share", "c2", "beacon", "firewall", "domain controller", "kerberos", "golden ticket", "silver ticket", "domain trust", "active directory", "ldap", "network boundary", "bgp hijack", "bgp hijack", "dns hijack", "dhcp poison", "forced authentication", "remote service", "network device", "vlan hopping", "protocol tunnel", "traffic signaling", "weaken encryption", "exploitation remote")),
    WEB_APP(List.of("sql injection", "cross-site script", "web shell", "csrf", "file upload vulnerability", "apache", "nginx", "iis", "php", "javascript", "rest api", "cookie", "server-side request forgery", "ssrf", "xml external entity", "xxe", "deserialization", "path traversal", "local file inclusion", "remote file inclusion", "template injection", "ssti", "api abuse", "drive-by compromise", "browser exploit", "forge web credential", "web service", "defacement", "server software component", "reverse proxy", "webdav", "session hijack")),
    EMAIL_INFILTRATION(List.of("spearphishing attachment", "spearphishing link", "phishing", "malicious attachment", "email account", "outlook", "exchange", "smtp", "mail server", "social engineering", "inbox rule", "dkim", "business email compromise", "bec", "email forwarding rule", "email delegation", "oauth consent", "reply-to manipulation", "email thread hijack", "internal spearphishing", "email collection", "zimbra", "mapi", "email template", "spoof sender", "dmarc", "spf", "email gateway", "link shortener")),
    DATA_EXFILTRATION(List.of("exfiltrat", "data staging", "data compressed", "steganography", "covert channel", "database dump", "automated collection", "intellectual property", "cloud storage exfil", "ftp exfil", "physical medium", "air gap", "scheduled transfer", "alternate protocol", "icmp tunnel", "dns exfiltration", "automated exfiltration", "web service exfil", "pastebin", "code repository", "cloud account transfer", "email exfil", "data destruction", "data encrypted", "image steganography")),
    URL_FILTERING(List.of("domain fronting", "url shorten", "typosquatting", "typosquatting", "homograph", "punycode", "url reputation", "content filter", "web gateway", "safe browsing", "url categorization", "blacklist bypass", "whitelist", "redirect", "proxy bypass", "dns over https", "doh", "dns over tls", "dot", "unicode domain", "url encode", "double encode", "open redirect", "captive portal", "proxy pac", "socks proxy", "tor", "vpn bypass", "domain generation", "fast flux", "url confusion", "subdomain takeover")),
    CLOUD(List.of("aws", "azure", "gcp", "lambda", "s3 bucket", "blob storage", "kubernetes", "docker", "serverless", "cloud instance", "iam role", "iam role", "saas", "tenant", "subscription", "api gateway", "microservice", "cloud trail", "cloudtrail", "cloud formation", "terraform", "cloud init", "metadata service", "instance metadata", "cloud api", "resource policy", "cloud dashboard", "unused region", "snapshot", "cloud backup", "object storage", "cloud function", "service principal", "managed identity", "cloud key", "sas token", "assume role"));

    private final List<String> keywords;

    DomainKeyWords(List<String> keywords) {
        this.keywords = keywords;
    }
}