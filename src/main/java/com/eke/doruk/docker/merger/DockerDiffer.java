/*
 * Copyright (C) 2023 SITKI DORUK EKE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eke.doruk.docker.merger;

import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

public class DockerDiffer {

    @SneakyThrows
    public static void main(String[] args) {
        if (args.length != 3) {
            exitSuccessWithLog("Usage: <reference_image>.tar <image_to_compare>.tar <target_image>.tar");
        }
        String referenceImagePath = args[0];
        String imageToComparePath = args[1];
        String targetImagePath = args[2];

        if (!new File(referenceImagePath).exists()) {
            failWithLog("Unable to find reference image: " + referenceImagePath);
        }

        if (!new File(imageToComparePath).exists()) {
            failWithLog("Unable to find image to compare : " + imageToComparePath);
        }

        if (new File(targetImagePath).exists() && !new File(targetImagePath).delete()) {
            failWithLog("Target file delete attempt failed: " + targetImagePath);
        }

        Set<String> referenceImageLayers = new HashSet<>();

        // Inspect reference
        try (FileInputStream fileInputStream = new FileInputStream(referenceImagePath)) {
            TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(fileInputStream);
            TarArchiveEntry tarEntry = null;
            while ((tarEntry = tarArchiveInputStream.getNextTarEntry()) != null) {
                if (tarEntry.isDirectory()) {
                    referenceImageLayers.add(tarEntry.getName());
                }
            }
        }

        Set<String> layersThatExistOnReference = new HashSet<>();

        // Inspect actual
        try (FileInputStream fileInputStream = new FileInputStream(imageToComparePath)) {
            TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(fileInputStream);
            TarArchiveEntry tarEntry = null;
            while ((tarEntry = tarArchiveInputStream.getNextTarEntry()) != null) {
                if (tarEntry.isDirectory()) {
                    String newTarDirectory = tarEntry.getName();
                    if (referenceImageLayers.contains(newTarDirectory)) {
                        layersThatExistOnReference.add(newTarDirectory);
                    }
                }
            }
        }


        try (
                FileInputStream fileInputStream = new FileInputStream(imageToComparePath);
                FileOutputStream fileOutputStream = new FileOutputStream(targetImagePath);
        ) {
            TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(fileInputStream);
            TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(fileOutputStream);
            TarArchiveEntry tarEntry = null;
            tarItems:
            while ((tarEntry = tarArchiveInputStream.getNextTarEntry()) != null) {
                for (String existingLayer : layersThatExistOnReference) {
                    if (tarEntry.getName().startsWith(existingLayer)) {
                        continue tarItems;
                    }
                }

                tarArchiveOutputStream.putArchiveEntry(tarEntry);

                if (!tarEntry.isDirectory()) {
                    IOUtils.copy(tarArchiveInputStream, tarArchiveOutputStream);
                }
                tarArchiveOutputStream.closeArchiveEntry();
                tarArchiveOutputStream.flush();

            }

            tarArchiveOutputStream.flush();
            tarArchiveOutputStream.close();
        }

    }

    public static void exitSuccessWithLog(String log) {
        System.out.println(log);
        System.exit(0);
    }


    public static void failWithLog(String log) {
        System.err.println(log);
        System.exit(-1);
    }
}
