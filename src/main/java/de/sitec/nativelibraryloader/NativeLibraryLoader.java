/*
 * Project: 13_0007_S1-STILL_Application
 * $Header: $
 * Author:  Mattes Standfuss
 * Last Change:
 *    by:   $Author: $
 *    date: $Date:   $
 * Copyright (c): sitec systems GmbH, 2013
 */
package de.sitec.nativelibraryloader;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An util for loading native library basend on system architecture and operating system.
 * It extracts the library from the JAR file to temporary directory.
 * The support of multiple library based on namespace convention. The path by 
 * convetion has following stucture at the JAR. <p><code>/native/namespace/os/
 * architecture/</code></p>
 * @author sitec systems GmbH
 * @since 1.0
 */
public class NativeLibraryLoader
{
    private static final String ROOT = "/native";
    private static final Map<String, Path> LOADED_LIBRARYS = new ConcurrentHashMap<>();
    private static final String HF_ABI_SUPPORT = "Tag_ABI_VFP_args: VFP registers";
    
    /**
     * Loads the input library based on namepsace. Use a lazy loading meachnism.
     * If a lirary is load at the first time its need the extracation. If the library
     * already previously loaed then no more extraction is neede and the extracted 
     * file will used.
     * @param namespace The namespace of the using software
     * @param library The name of the native library file
     * @since 1.0
     */
    public static void loadLibrary(final String namespace, final String library) 
    {
        System.out.println("Load native library: '" + namespace + "/" + library);
        
        Path path = LOADED_LIBRARYS.get(namespace + "/" + library);
        
        try
        {
            if(path == null)
            {
                path = extractNativeLibrary(namespace, library);
                LOADED_LIBRARYS.put(namespace + "/" + library, path);
            }

            System.out.println("Path: " + path.toString());
            
            System.load(path.toString());
        }
        catch (final IOException ex)
        {
            throw new UnsatisfiedLinkError("The library " + namespace + "/" + library 
                    + " was not found: " + ex.getMessage());
        }

        System.out.println("Native library: '" + namespace + "/" + library + "' loaded");
    }
    
    /**
     * Extracts the library from the JAR file based on namespace convention.
     * @param namespace The namespace of the using software
     * @param library The name of the native library file
     * @return The temporary local path
     * @throws IOException If the extraction has failed
     * @since 1.0
     */
    private static Path extractNativeLibrary(final String namespace, final String library) 
            throws IOException
    {
        final String os = System.getProperty("os.name");
        final String architecture = getArchitecture(System.getProperty("os.arch"));
        
        System.out.println("OS: " + os);
        System.out.println("Architecture: " + architecture);
        
        final OsData osData = OsData.getOsData(os);
        
        final StringBuilder sb = new StringBuilder(ROOT);
        sb.append("/");
        sb.append(namespace.toLowerCase());
        sb.append("/");
        sb.append(osData.os);
        sb.append("/");
        sb.append(architecture);
        sb.append("/");
        sb.append(library);
        sb.append(".");
        sb.append(osData.getFileExtension());
        
        final String resourcePath = sb.toString();
        
        System.out.println("ResourcePath: " + resourcePath);
        
        Path result;
        
        try(final InputStream resourceInputStream = new BufferedInputStream(
                NativeLibraryLoader.class.getResourceAsStream(resourcePath)))
        {
            final Path tempDirectory = Files.createTempDirectory(library);
            tempDirectory.toFile().deleteOnExit();
            final Path tempFile = Files.createTempFile(tempDirectory, library, null);
            tempFile.toFile().deleteOnExit();
            Files.copy(resourceInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            result = tempFile;
        }
        
        return result;
    }
    
    private static String getArchitecture(final String archIndicator) 
            throws IOException
    {
        final String result;
        
        if(archIndicator.toLowerCase().contains("arm"))
        {
            if (isHardFloatAbi()) 
            {
                result = "armhf";
            } 
            else 
            {
                result = "armel";
            }
        }
        else
        {
            result = archIndicator.toLowerCase();
        }
        
        return result;
    }

    /**
     * Checks the platform for hard float abi support.
     * @return <code>true<code> - The hard float abi is supported / <code>false</code>
     *         - Only soft float abi is supported
     * @throws IOException The reading of abi from ELF header has failed
     * @since 1.0
     */
    private static boolean isHardFloatAbi() throws IOException 
    {
        final ProcessBuilder pb = new ProcessBuilder("readelf", "-A", "/proc/self/exe");
        final Process p = pb.start();
        
        try 
        {
            p.waitFor();
        } 
        catch (final InterruptedException ex) 
        {
            System.out.println("The ABI request was interrupted");
        }

        if(p.exitValue() != 0) 
        {
            throw new IOException("The ABI request has failed");
        }
        
        try (final BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream())))
        {
            String line;
            boolean result = false;
            
            while((line = br.readLine()) != null) 
            {
                if(line.contains(HF_ABI_SUPPORT)) 
                {
                    result = true;
                    break;
                }
            }
            
            return result;
        }
    }

    /**
     * An enumeration for mapping between os and according file extension of 
     * native librarys.
     * @since 1.0
     */
    private enum OsData
    {
        WINDOWS("windows", "dll"), LINUX("linux", "so"), MAC_OS_X("os x", "jnilib"), SOLARIS("solaris", "so");
        
        private final String os;
        private final String fileExtension;

        private OsData(final String os, final String fileExtension)
        {
            this.os = os;
            this.fileExtension = fileExtension;
        }

        /**
         * Gets the os.
         * @return The os
         * @since 1.0
         */
        private String getOs()
        {
            return os;
        }

        /**
         * Gets the native library file extension.
         * @return The native library file extension
         * @since 1.0
         */
        private String getFileExtension()
        {
            return fileExtension;
        }
        
        /**
         * Gets the <code>OsData</code> based on os indicator.
         * @param osIndicator The os indicator
         * @return The <code>OsData</code>
         * @since 1.0
         */
        private static OsData getOsData(final String osIndicator)
        {
            final OsData result;

            if(osIndicator.toLowerCase().contains("windows"))
            {
                result = WINDOWS;
            }
            else if (osIndicator.toLowerCase().contains("linux"))
            {
                result = LINUX;
            }
            else if (osIndicator.toLowerCase().contains("solaris"))
            {
                result = SOLARIS;
            }
            else if (osIndicator.toLowerCase().contains("os x"))
            {
                result = MAC_OS_X;
            }
            else
            {
                throw new IllegalArgumentException("OS is not supported");
            }

            return result;
        }
    }
}
