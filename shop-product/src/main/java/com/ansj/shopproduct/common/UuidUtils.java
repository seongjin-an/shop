package com.ansj.shopproduct.common;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

public class UuidUtils {
    public static UUID createV7() {
        return Generators.timeBasedEpochGenerator().generate();
    }
}
