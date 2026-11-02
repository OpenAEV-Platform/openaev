package io.openaev.security.ssrf;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resolves a host name to its addresses, like {@link InetAddress#getAllByName(String)}. */
@FunctionalInterface
public interface HostResolver {

  InetAddress[] resolve(String host) throws UnknownHostException;
}
