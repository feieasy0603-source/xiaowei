package com.xiaowei.util;

import java.util.UUID;

public final class BusinessIdGenerator {

    private BusinessIdGenerator() {
    }

    public static String orderNo() {
        return randomId("O");
    }

    public static String jobNo() {
        return randomId("J");
    }

    public static String walletRechargeNo() {
        return randomId("WR");
    }

    private static String randomId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
    }
}
