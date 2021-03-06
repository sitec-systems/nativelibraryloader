/**
 * NativeLibraryLoader is an framework for handling of JNI dependend native 
 * librarys.
 * 
 * Copyright (C) 2016 sitec systems GmbH <http://www.sitec-systems.de>
 * 
 * This file is part of NativeLibraryLoader.
 * 
 * NativeLibraryLoader is free software: you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by the 
 * Free Software Foundation, either version 3 of the License, or (at your option) 
 * any later version.
 * 
 * NativeLibraryLoader is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more 
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with NativeLibraryLoader. If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * Author: Mattes Standfuss
 * Copyright (c): sitec systems GmbH, 2016
 */
package de.sitec_systems.nativelibraryloader;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An loader for loading native librarys. In default case  the loader loads the 
 * native library from JAR file. The following path structure is necessary in 
 * JAR file. 
 * <p>The path structure in JAR file must be like 
 *  <code>.../native/&lt;namespace&gt;/&lt;os&gt;/&lt;architecture&gt;/</code>
 * </p>
 * <p>
 *  for example: <code>.../native/com/company/library/windows/amd64/</code>.
 * </p>
 * 
 * <p>If a native library has dependency to other native librarys, then its possible 
 * to extract dependend native librarys before load the needed native library.
 * For Example:</p>
 * 
 * <pre><code>
 *  NativeLibraryLoader.extractLibrary("com.company.library", "dependendLibraryA");
 *  NativeLibraryLoader.extractLibrary("com.company.library", "dependendLibraryB");
 *  NativeLibraryLoader.loadLibrary("com.company.library", "libraryWithDependency");
 * </code></pre>
 * 
 * It is possible to configure the loader for loading the native library from custom
 * resource. Following system properties are available.
 * <table border="1">
 *  <caption>System properties for native library loader</caption>
 *  <tr>
 *      <th>Key</th>
 *      <th>Description</th>
 *  </tr>
 *  <tr>
 *      <td><code>native_library_loader_&lt;namespace&gt;_arch_detect</code></td>
 *      <td>
 *          If the value set to true, then it will resolve automatically the path
 *          to the native library based on operating system and system architecture
 *      </td>
 *  </tr>
 * <tr>
 *      <td><code>native_library_loader_&lt;namespace&gt;_path</code></td>
 *      <td>
 *          If the property arch_dectect true, then path must point to root 
 *          of the native library loader directory structure like JAR file path
 *          structure. This means the start folder <code>.../native/...</code>.
 *          If the automatic path resolving disabled then path must point to the 
 *          folder that contains the native library.
 *      </td>
 *  </tr>
 * </table>
 * @author sitec systems GmbH
 * @since 1.0
 */
public class NativeLibraryLoader
{
    private static final Map<String, Path> LOADED_NAMESPACES = new ConcurrentHashMap<>();
    private static final Map<String, Path> LOADED_LIBRARYS = new ConcurrentHashMap<>();
    private static final String PROPERTY_KEY = "native_library_loader";
    private static final String ARCH_DETECT_KEY = "arch_detect";
    private static final String PATH_KEY = "path";
    private static final Path ROOT_DIR = Paths.get(System.getProperty("java.io.tmpdir")).resolve(PROPERTY_KEY);
    private static final String OS = System.getProperty("os.name");
    private static final String ARCHITECTURE = getArchitecture(System.getProperty("os.arch"));
    private static final String ROOT = "native";
    private static final String HF_ABI_SUPPORT = "Tag_ABI_VFP_args: VFP registers";
    private static final Logger LOG = LoggerFactory.getLogger(NativeLibraryLoader.class);
    private static final byte DIR_CREATING_TRAILS = 20;
    private static final String NAMESPACE_PATTERN = "[\\w+.]+";
    
    /**
     * Creates a temp directory for the namespace in the operating system temp 
     * directory. For example:
     * 
     * <code>.../tmp/native_library_loader/com/company/library/0</code>
     * 
     * If a temp directory already exists for the namespace then it trys to delete 
     * them. If the deleting impossible (e. g. an other instance holds access 
     * to the directory) then creates a new temp directory with next index folder.
     * @param namespace The namespace for the library like com.company.library
     * @return The created temp directory
     * @throws IOException The creating of the temp directory has failed
     * @since 1.0
     */
    private static Path createTempDirectory(final String namespace) throws IOException
    {
        final Path tempDir = ROOT_DIR.resolve(namespace.replace('.', '/'));
        
        for(int i=0; i<DIR_CREATING_TRAILS; i++)
        {
            final Path tempDirPath = tempDir.resolve(Integer.toString(i));
        
            try 
            {
                if(Files.exists(tempDirPath))
                {
                    deleteDirectory(tempDirPath);
                }

                LOG.debug("Create temp directory: '{}' for package: '{}'", tempDirPath
                        , namespace);

                final Path tempDirectory = Files.createDirectories(tempDirPath);
                tempDirectory.toFile().deleteOnExit();

                return tempDirectory;
            }
            catch (final IOException ex) 
            {
                if(i == DIR_CREATING_TRAILS - 1)
                {
                    throw new IOException("Creating a temp directory has failed - Trails: " 
                            + DIR_CREATING_TRAILS, ex);
                }
            }
        }
        
        throw new IOException("Creating a temp directory has failed for unkown reason");
    }
    
    /**
     * Deletes the input directory recursive with all content.
     * @param path The path of the directory to delete
     * @throws IOException The deleting of the directory has failed
     * @since 1.0
     */
    private static void deleteDirectory(final Path path) throws IOException
    {
        LOG.debug("Delete: '{}'", path);
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() 
        {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                    throws IOException
            {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException e)
                    throws IOException
            {
                if (e == null) 
                {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                } 
                else 
                {
                    throw e;
                }
            }
        });
        LOG.debug("Deleted: '{}'", path);
    }
    
    /**
     * Loads the input library based on namespace. Use a lazy loading meachnism.
     * If a library is load at the first time its need the extraction from JAR file. 
     * If the library already previously loaded, then no more extraction is needed
     * and the extracted file will used.
     * @param namespace The namespace for the library like com.company.library
     * @param library The name of the native library file
     * @since 1.0
     */
    public synchronized static void loadLibrary(final String namespace, final String library) 
    {
        if(!namespace.matches(NAMESPACE_PATTERN))
        {
            throw new IllegalArgumentException("Invalid namespace: '" + namespace + "' - needed like: 'com.company.library'");
        }
        
        LOG.info("Load native library: '{}.{}'", namespace, library);
        
        Path path = LOADED_LIBRARYS.get(namespace + "." + library);
        
        try
        {
            if(path == null)
            {
                final String customProperty = System.getProperty(PROPERTY_KEY + "_" 
                        + namespace + "_" + PATH_KEY);
                if(customProperty != null && !customProperty.isEmpty())
                {
                    LOG.info("Load native library: '{}.{}' from custom resource"
                            , namespace, library);
                    final String archDetectProperty = System.getProperty(PROPERTY_KEY 
                            + "_" + namespace + "_" + ARCH_DETECT_KEY);
                    if(archDetectProperty != null && !archDetectProperty.isEmpty()
                            && archDetectProperty.contains("true"))
                    {
                        path = Paths.get(customProperty, getPathString(namespace, library));
                    }
                    else
                    {
                        path = Paths.get(customProperty, library + "."
                            + OsData.getOsData(OS).getFileExtension());
                    }
                    
                    if(!Files.exists(path))
                    {
                        LOG.warn("Native library: '{}.{}' not available at custom resource: '{}'"
                            , namespace, library, path);
                        LOG.info("Load native library: '{}.{}' from JAR resource"
                            , namespace, library);
                        path = extractLibrary(namespace, library);
                    }
                }
                else
                {
                    LOG.info("Load native library: '{}.{}' from JAR resource"
                            , namespace, library);
                    path = extractLibrary(namespace, library);
                }
                LOADED_LIBRARYS.put(namespace + "." + library, path);
            }

            LOG.debug("Path to native library: '{}'", path.toString());
            
            System.load(path.toString());
        }
        catch (final IOException ex)
        {
            throw new UnsatisfiedLinkError("Loading the library " + namespace + "." 
                    + library + " has failed: " + ex.getMessage());
        }

        LOG.info("Native library: '{}.{}' loaded", namespace, library);
    }
    
    /**
     * Extracts the library from the JAR file based on namespace convention.
     * @param namespace The namespace for the library like com.company.library
     * @param library The name of the native library file
     * @return The temporary local path
     * @throws IOException If the extraction has failed
     * @since 1.1
     */
    public synchronized static Path extractLibrary(final String namespace, final String library) 
            throws IOException
    { 
        final String resourcePath = getPathString(namespace, library);
        
        LOG.debug("Extract native library '{}.{}' from JAR ResourcePath: '{}'"
                , namespace, library, resourcePath);
        
        if(NativeLibraryLoader.class.getResource(resourcePath) != null)
        {
            final Path tempDirectory;
            
            if(LOADED_NAMESPACES.containsKey(namespace))
            {
                tempDirectory = LOADED_NAMESPACES.get(namespace);
            }
            else
            {
                tempDirectory = createTempDirectory(namespace);
                LOADED_NAMESPACES.put(namespace, tempDirectory);
            }
            
            Path result;

            try(final InputStream resourceInputStream = new BufferedInputStream(
                    NativeLibraryLoader.class.getResourceAsStream(resourcePath)))
            {
                final Path tempFile = Paths.get(URI.create(tempDirectory.toUri() 
                        + library + "." + OsData.getOsData(OS).getFileExtension()));
                Files.createFile(tempFile);
                tempFile.toFile().deleteOnExit();
                Files.copy(resourceInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);   

                result = tempFile;
            }
            
            LOG.debug("Native library '{}.{}' extracted to: '{}'", namespace
                    , library, result.toString());

            return result;
        }
        else
        {
            throw new FileNotFoundException("The native resource: '" + resourcePath
                    + "' was not found in .jar");
        }
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
    {
        final String result;
        
        if(archIndicator.toLowerCase().contains("arm"))
        {
            try 
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
            catch (final IOException ex) 
            {
                throw new RuntimeException("Detecting ARM architecture has failed", ex);
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
     * Gets a path to a native library based on the local operating system and 
     * system architecture.
     * The path structure must be like <code>.../native/&lt;namespace&gt;/&lt;os&gt;/&lt;architecture&gt;/</code>
     * for example: <code>.../native/com/company/library/windows/amd64/</code>
     * @param namespace The namespace for the library like com.company.library
     * @param library The name of the native library file
     * @return The path string to native library
     * @since 1.0
     */
    private static String getPathString(final String namespace, final String library)
    {
        final OsData osData = OsData.getOsData(OS);
        
        final StringBuilder sb = new StringBuilder("/");
        sb.append(ROOT);
        sb.append("/");
        sb.append(namespace.replace(".", "/"));
        sb.append("/");
        sb.append(osData.os);
        sb.append("/");
        sb.append(ARCHITECTURE);
        sb.append("/");
        sb.append(library);
        sb.append(".");
        sb.append(osData.getFileExtension());
        
        return sb.toString();
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
    
    static
    {
        LOG.info("OS: {}, Architecture: {}", OS, ARCHITECTURE);
    }
}
