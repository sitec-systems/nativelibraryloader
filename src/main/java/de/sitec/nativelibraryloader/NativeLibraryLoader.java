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

import java.io.Closeable;
import java.io.IOException;

/**
 * An interface for handling of loading native librarys.
 * @author sitec systems GmbH
 * @since 1.0
 */
public interface NativeLibraryLoader extends Closeable
{
    /**
     * Loads the native library.
     * @param library The name of the native library
     * @throws IOException The loading has failed
     * @since 1.0
     */
    void loadLibrary(final String library) throws IOException;
}
