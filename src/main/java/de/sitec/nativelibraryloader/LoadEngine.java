/*
 * Project: 14_0017_NativeLibraryLoader
 * $Header: $
 * Author:  Mattes Standfuss
 * Last Change:
 *    by:   $Author: $
 *    date: $Date:   $
 * Copyright (c): sitec systems GmbH, 2014
 */
package de.sitec.nativelibraryloader;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains the pure loading logic for loading of native librarys.
 * @author sitec systems GmbH
 * @since 1.0
 */
public class LoadEngine
{
    private final String namespace;
    private final Path tempDirectory;
    private final Map<String, Path> LOADED_LIBRARYS = new ConcurrentHashMap<>();
    private final String os = System.getProperty("os.name");
    private final String architecture = getArchitecture(System.getProperty("os.arch"));
    
    private static final String ROOT = "native";
    private static final String HF_ABI_SUPPORT = "Tag_ABI_VFP_args: VFP registers";
    private static final Logger LOG = LoggerFactory.getLogger(LoadEngine.class);

    /**
     * Constructor. Creates an temp directory for the extraction of native librarys.
     * @param namespace The namespace of the project (e.g. de.sitec.nativelibraryloader)
     * @throws IOException The creation of temp directory has failed
     * @since 1.0
     */
    public LoadEngine(final String namespace) throws IOException
    {
        LOG.info("OS: " + os);
        LOG.info("Architecture: " + architecture);
        this.namespace = getWellformedNamespace(namespace);
        final String tempDir = this.namespace.substring(this.namespace.lastIndexOf('.') + 1) + "_";
        LOG.debug("Create temp directory: " + tempDir + " for package: " + this.namespace); 
        tempDirectory = Files.createTempDirectory(tempDir);
        tempDirectory.toFile().deleteOnExit();
    }
    
    /**
     * Replaces unwanted chars from namespace. Well formed mean dot notation like
     * de.sitec.nativelibrary.
     * @param namespace The namespace in supported format
     * @return The well formed namespace
     * @since 1.0
     */
    private static String getWellformedNamespace(final String namespace)
    {
        return namespace.replace("/", ".");
    }
    
    /**
     * Loads the input library based on namepspace. Use a lazy loading meachnism.
     * If a library is load at the first time its need the extracation. If the library
     * already previously loaed then no more extraction is neede and the extracted 
     * file will used.
     * @param library The name of the native library file
     * @since 1.0
     */
    public void loadLibrary(final String library) 
    {
        LOG.info("Load native library: '" + namespace + "." + library);
        
        Path path = LOADED_LIBRARYS.get(namespace + "." + library);
        
        try
        {
            if(path == null)
            {
                path = extractNativeLibrary(library);
                LOADED_LIBRARYS.put(namespace + "." + library, path);
            }

            LOG.debug("Path: " + path.toString());
            
            System.load(path.toString());
        }
        catch (final IOException ex)
        {
            throw new UnsatisfiedLinkError("The library " + namespace + "." + library 
                    + " was not found: " + ex.getMessage());
        }

        LOG.info("Native library: '" + namespace + "." + library + "' loaded");
    }
    
    /**
     * Extracts the library from the JAR file based on namespace convention.
     * @param namespace The namespace of the using software
     * @param library The name of the native library file
     * @return The temporary local path
     * @throws IOException If the extraction has failed
     * @since 1.0
     */
    private Path extractNativeLibrary(final String library) 
            throws IOException
    {
        final OsData osData = OsData.getOsData(os);
        
        final StringBuilder sb = new StringBuilder("/");
        sb.append(ROOT);
        sb.append("/");
        sb.append(namespace.replace(".", "/"));
        sb.append("/");
        sb.append(osData.os);
        sb.append("/");
        sb.append(architecture);
        sb.append("/");
        sb.append(library);
        sb.append(".");
        sb.append(osData.getFileExtension());
        
        final String resourcePath = sb.toString();
        
        LOG.debug("ResourcePath: " + resourcePath);
        
        Path result;
        
        try(final InputStream resourceInputStream = new BufferedInputStream(
                NativeLibraryLoaderImpl.class.getResourceAsStream(resourcePath)))
        {
            final Path tempFile = Paths.get(URI.create(tempDirectory.toUri() + library + ".dll"));
            Files.createFile(tempFile);
            tempFile.toFile().deleteOnExit();
            Files.copy(resourceInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);   
            
            result = tempFile;
        }
        
        return result;
    }
    
    /**
     * Special treatment for the <b>ARM</b> architecture. Find out the support 
     * between hard float abi and soft float abi. On other architecture the
     * method returns the unchanged input <code>String</code>.
     * @param archIndicator The architecture
     * @return The architecture inclusive special treatment for <b>ARM</b> architecture
     * @throws IOException The detection of float abi has failed
     * @since 1.0
     */
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
            LOG.error("The ABI request was interrupted", ex);
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
