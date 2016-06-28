# About

**NativeLibrarLoader** is an framework for handling of JNI dependend native librarys.

The framework makes possible to package the needed JNI native librarys to the JAR package. The NativeLibraryLoader resolves the operating system and system architecure dependencies automatically based on directory structure. The Loader can be used in different dependencys without conflicts. The seperation is based on namespaces.

**more documentation is available on overview page of javaDoc**

# License

[LGPLv3](http://www.gnu.org/licenses/lgpl.html)

Copyright (C) 2016 sitec systems GmbH

# Example

The native library must be packaged to JAR in following structure:

```
<jar-root>/native/<namespace>/<os>/<architecture>/
```

for example:

```
.../native/com/company/library/linux/amd64/library.so
```

Load native librarys:

```java
NativeLibraryLoader.extractLibrary("com.company.library", "library");
NativeLibraryLoader.loadLibrary("com.company.library", "library");
```
