package com.shorty.url;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;

final class SsrfAddresses {

    private SsrfAddresses() {}

    static boolean blocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address v6) {
            if (isUniqueLocal(v6) || isIpv4MappedPrivate(v6) || isEc2MetadataIpv6(v6)) {
                return true;
            }
        }
        if (address instanceof Inet4Address v4 && isMetadataIpv4(v4)) {
            return true;
        }
        return false;
    }

    static boolean isUniqueLocal(Inet6Address address) {
        int b0 = address.getAddress()[0] & 0xff;
        return (b0 & 0xfe) == 0xfc;
    }

    static boolean isEc2MetadataIpv6(Inet6Address address) {
        // fd00:ec2::254
        byte[] a = address.getAddress();
        return (a[0] & 0xff) == 0xfd
                && (a[1] & 0xff) == 0x00
                && (a[2] & 0xff) == 0x0e
                && (a[3] & 0xff) == 0xc2
                && a[15] == (byte) 0xfe
                && allZero(a, 4, 15);
    }

    static boolean isMetadataIpv4(Inet4Address address) {
        byte[] a = address.getAddress();
        return (a[0] & 0xff) == 169 && (a[1] & 0xff) == 254 && (a[2] & 0xff) == 169 && (a[3] & 0xff) == 254;
    }

    static boolean isIpv4MappedPrivate(Inet6Address address) {
        byte[] a = address.getAddress();
        if (!isIpv4Mapped(a)) {
            return false;
        }
        try {
            InetAddress v4 = InetAddress.getByAddress(Arrays.copyOfRange(a, 12, 16));
            return blocked(v4);
        } catch (Exception e) {
            return true;
        }
    }

    static boolean isIpv4Mapped(byte[] a) {
        if (a.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (a[i] != 0) {
                return false;
            }
        }
        return (a[10] & 0xff) == 0xff && (a[11] & 0xff) == 0xff;
    }

    private static boolean allZero(byte[] a, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (a[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
