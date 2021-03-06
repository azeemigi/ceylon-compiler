/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.redhat.ceylon.compiler.java.test.structure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerTest;
import com.redhat.ceylon.compiler.java.tools.CeyloncTaskImpl;
import com.redhat.ceylon.compiler.java.util.Util;
import com.sun.net.httpserver.HttpServer;

public class StructureTest extends CompilerTest {
    
    //
    // Packages
    
    @Test
    public void testPkgPackage(){
        compareWithJavaSource("pkg/pkg");
    }

    @Test
    public void testPkgPackageMetadata(){
        compareWithJavaSource("pkg/package");
    }

    //
    // Modules
    
    @Test
    public void testMdlModule(){
        compareWithJavaSource("module/single/module");
    }

    @Test
    public void testMdlModuleDefault() throws IOException{
        compile("module/def/CeylonClass.ceylon");
        
        File carFile = getModuleArchive("default", null);
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/def/CeylonClass.class");
        assertNotNull(moduleClass);
        car.close();
    }

    @Test
    public void testMdlModuleDefaultJavaFile() throws IOException{
        compile("module/def/JavaClass.java");
        
        File carFile = getModuleArchive("default", null);
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/def/JavaClass.class");
        assertNotNull(moduleClass);
        car.close();
    }

    @Test
    public void testMdlModuleOnlyInOutputRepo(){
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6");
        assertFalse(carFile.exists());

        File carFileInHomeRepo = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6",
                Util.getHomeRepository());
        if(carFileInHomeRepo.exists())
            carFileInHomeRepo.delete();
        
        compile("module/single/module.ceylon");

        // make sure it was created in the output repo
        assertTrue(carFile.exists());
        // make sure it wasn't created in the home repo
        assertFalse(carFileInHomeRepo.exists());
    }
    
    @Test
    public void testMdlModuleFromCompiledModule() throws IOException{
        compile("module/single/module.ceylon");
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);
        // just to be sure
        ZipEntry bogusEntry = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/BOGUS");
        assertNull(bogusEntry);

        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/module.class");
        assertNotNull(moduleClass);
        car.close();

        compile("module/single/subpackage/Subpackage.ceylon");

        // MUST reopen it
        car = new JarFile(carFile);

        ZipEntry subpackageClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/subpackage/Subpackage.class");
        assertNotNull(subpackageClass);

        car.close();
    }

    @Test
    public void testMdlCompilerGeneratesModuleForValidUnits() throws IOException{
        CeyloncTaskImpl compilerTask = getCompilerTask("module/single/module.ceylon", "module/single/Correct.ceylon", "module/single/Invalid.ceylon");
        Boolean success = compilerTask.call();
        assertFalse(success);
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/module.class");
        assertNotNull(moduleClass);

        ZipEntry correctClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/Correct.class");
        assertNotNull(correctClass);

        ZipEntry invalidClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/Invalid.class");
        assertNull(invalidClass);
        
        car.close();
    }

    private File getModuleArchive(String moduleName, String version) {
        return getModuleArchive(moduleName, version, destDir);
    }

    private File getModuleArchive(String moduleName, String version, String destDir) {
        return getArchiveName(moduleName, version, destDir, "car");
    }

    private File getSourceArchive(String moduleName, String version) {
        return getArchiveName(moduleName, version, destDir, "src");
    }
    
    private File getSourceArchive(String moduleName, String version, String destDir) {
        return getArchiveName(moduleName, version, destDir, "src");
    }

    private File getArchiveName(String moduleName, String version, String destDir, String extension) {
        String modulePath = moduleName.replace('.', File.separatorChar);
        if(version != null)
            modulePath += File.separatorChar+version;
        modulePath += File.separator;
        String artifactName = modulePath+moduleName;
        if(version != null)
            artifactName += "-"+version;
        artifactName += "."+extension;
        File archiveFile = new File(destDir, artifactName);
        return archiveFile;
    }

    @Test
    public void testMdlInterdepModule(){
        // first compile it all from source
        compile("module/interdep/a/module.ceylon", "module/interdep/a/package.ceylon", "module/interdep/a/b.ceylon", "module/interdep/a/A.ceylon",
                "module/interdep/b/module.ceylon", "module/interdep/b/package.ceylon", "module/interdep/b/a.ceylon", "module/interdep/b/B.ceylon");
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.interdep.a", "6.6.6");
        assertTrue(carFile.exists());

        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.interdep.b", "6.6.6");
        assertTrue(carFile.exists());
        
        // then try to compile only one module (the other being loaded from its car) 
        compile("module/interdep/a/module.ceylon", "module/interdep/a/b.ceylon", "module/interdep/a/A.ceylon");
    }

    @Test
    public void testMdlDependentModule(){
        // Compile only the first module 
        compile("module/depend/a/module.ceylon", "module/depend/a/package.ceylon", "module/depend/a/A.ceylon");
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.depend.a", "6.6.6");
        assertTrue(carFile.exists());

        // then try to compile only one module (the other being loaded from its car) 
        compile("module/depend/b/module.ceylon", "module/depend/b/a.ceylon", "module/depend/b/aWildcard.ceylon");

        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.depend.b", "6.6.6");
        assertTrue(carFile.exists());
    }

    @Test
    public void testMdlImplicitDependentModule(){
        // Compile only the first module 
        compile("module/implicit/a/module.ceylon", "module/implicit/a/package.ceylon", "module/implicit/a/A.ceylon",
                "module/implicit/b/module.ceylon", "module/implicit/b/package.ceylon", "module/implicit/b/B.ceylon", "module/implicit/b/B2.ceylon",
                "module/implicit/c/module.ceylon", "module/implicit/c/package.ceylon", "module/implicit/c/c.ceylon");
        
        // Dependencies:
        //
        // c.ceylon--> B2.ceylon
        //         |
        //         '-> B.ceylon  --> A.ceylon

        // Successfull tests :
        
        compile("module/implicit/c/c.ceylon");
        compile("module/implicit/b/B.ceylon", "module/implicit/c/c.ceylon");
        compile("module/implicit/b/B2.ceylon", "module/implicit/c/c.ceylon");
        
        // Failing tests :
        
        Boolean success1 = getCompilerTask("module/implicit/c/c.ceylon", "module/implicit/b/B.ceylon").call();
        // => B.ceylon : package not found in dependent modules: com.redhat.ceylon.compiler.java.test.structure.module.implicit.a
        Boolean success2 = getCompilerTask("module/implicit/c/c.ceylon", "module/implicit/b/B2.ceylon").call();
        // => c.ceylon : TypeVisitor caused an exception visiting Import node: com.sun.tools.javac.code.Symbol$CompletionFailure: class file for com.redhat.ceylon.compiler.test.structure.module.implicit.a.A not found at unknown

        Assert.assertTrue(success1 && success2);
    }

    private void copy(File source, File dest) throws IOException {
        InputStream inputStream = new FileInputStream(source);
        OutputStream outputStream = new FileOutputStream(dest); 
        byte[] buffer = new byte[4096];
        int read;
        while((read = inputStream.read(buffer)) != -1){
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        outputStream.close();
    }
    
    @Test
    public void testMdlSuppressObsoleteClasses() throws IOException{
        File sourceFile = new File(path, "module/single/SuppressClass.ceylon");

        copy(new File(path, "module/single/SuppressClass_1.ceylon"), sourceFile);
        CeyloncTaskImpl compilerTask = getCompilerTask("module/single/module.ceylon", "module/single/SuppressClass.ceylon");
        Boolean success = compilerTask.call();
        assertTrue(success);

        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6");
        assertTrue(carFile.exists());
        ZipFile car = new ZipFile(carFile);
        ZipEntry oneClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/One.class");
        assertNotNull(oneClass);
        ZipEntry twoClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/Two.class");
        assertNotNull(twoClass);
        car.close();

        copy(new File(path, "module/single/SuppressClass_2.ceylon"), sourceFile);
        compilerTask = getCompilerTask("module/single/module.ceylon", "module/single/SuppressClass.ceylon");
        success = compilerTask.call();
        assertTrue(success);
        
        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6");
        assertTrue(carFile.exists());
        car = new ZipFile(carFile);
        oneClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/One.class");
        assertNotNull(oneClass);
        twoClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/Two.class");
        assertNull(twoClass);
        car.close();
        
        sourceFile.delete();
    }

    
    @Test
    public void testMdlMultipleRepos(){
        cleanCars("build/ceylon-cars-a");
        cleanCars("build/ceylon-cars-b");
        cleanCars("build/ceylon-cars-c");
        
        // Compile the first module in its own repo 
        File repoA = new File("build/ceylon-cars-a");
        repoA.mkdirs();
        Boolean result = getCompilerTask(Arrays.asList("-out", repoA.getPath()),
                "module/depend/a/module.ceylon", "module/depend/a/package.ceylon", "module/depend/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.depend.a", "6.6.6", repoA.getPath());
        assertTrue(carFile.exists());

        // make another repo for the second module
        File repoB = new File("build/ceylon-cars-b");
        repoB.mkdirs();

        // then try to compile only one module (the other being loaded from its car) 
        result = getCompilerTask(Arrays.asList("-out", repoB.getPath(), "-rep", repoA.getPath()),
                "module/depend/b/module.ceylon", "module/depend/b/package.ceylon", "module/depend/b/a.ceylon", "module/depend/b/B.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.depend.b", "6.6.6", repoB.getPath());
        assertTrue(carFile.exists());

        // make another repo for the third module
        File repoC = new File("build/ceylon-cars-c");
        repoC.mkdirs();

        // then try to compile only one module (the others being loaded from their car) 
        result = getCompilerTask(Arrays.asList("-out", repoC.getPath(), 
                "-rep", repoA.getPath(), "-rep", repoB.getPath()),
                "module/depend/c/module.ceylon", "module/depend/c/a.ceylon", "module/depend/c/b.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);

        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.depend.c", "6.6.6", repoC.getPath());
        assertTrue(carFile.exists());
    }

    @Test
    public void testMdlHTTPRepos() throws IOException{
        String moduleA = "com.redhat.ceylon.compiler.java.test.structure.module.depend.a";
        
        // Clean up any cached version
        File carFileInHomeRepo = getModuleArchive(moduleA, "6.6.6", Util.getHomeRepository());
        if(carFileInHomeRepo.exists())
            carFileInHomeRepo.delete();

        // Compile the first module in its own repo 
        File repoA = new File("build/ceylon-cars-a");
        cleanCars(repoA.getPath());
        repoA.mkdirs();
        
        Boolean result = getCompilerTask(Arrays.asList("-out", repoA.getPath()),
                "module/depend/a/module.ceylon", "module/depend/a/package.ceylon", "module/depend/a/A.ceylon").call();
        Assert.assertEquals(Boolean.TRUE, result);
        
        File carFile = getModuleArchive(moduleA, "6.6.6", repoA.getPath());
        assertTrue(carFile.exists());

        // now serve the first repo over HTTP
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 1);
        server.createContext("/repo", new RepoFileHandler(repoA.getPath()));
        server.setExecutor(null); // creates a default executor
        server.start();
        
        String repoAURL = "http://localhost:8000/repo";
        
        try{
            // then try to compile only one module (the other being loaded from its car) 
            result = getCompilerTask(Arrays.asList("-out", destDir, "-rep", repoAURL),
                    "module/depend/b/module.ceylon", "module/depend/b/package.ceylon", "module/depend/b/a.ceylon", "module/depend/b/B.ceylon").call();
            Assert.assertEquals(Boolean.TRUE, result);

        }finally{
            server.stop(0);
        }
        carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.depend.b", "6.6.6");
        assertTrue(carFile.exists());
        
        // make sure it cached the module in the home repo
        assertTrue(carFileInHomeRepo.exists());
        
        // make sure it didn't cache it in the output repo
        carFile = getModuleArchive(moduleA, "6.6.6");
        assertFalse(carFile.exists());
    }

    @Test
    public void testMdlHTTPOutputRepo() throws IOException{
        // Compile the first module in its own repo 
        File repoA = new File("build/ceylon-cars-a");
        cleanCars(repoA.getPath());
        repoA.mkdirs();

        // now serve the first repo over HTTP
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 1);
        server.createContext("/repo", new RepoFileHandler(repoA.getPath()));
        server.setExecutor(null); // creates a default executor
        server.start();
        
        String repoAURL = "http://localhost:8000/repo";
        
        try{
            // then try to compile our module by outputting to HTTP 
            Boolean result = getCompilerTask(Arrays.asList("-out", repoAURL), "module/single/module.ceylon").call();
            Assert.assertEquals(Boolean.TRUE, result);

        }finally{
            server.stop(0);
        }
        
        File carFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6", repoA.getPath());
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        // make sure it's not empty
        ZipEntry moduleClass = car.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/module.class");
        assertNotNull(moduleClass);
        car.close();

        File srcFile = getSourceArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6", repoA.getPath());
        assertTrue(srcFile.exists());
    }

    @Test
    public void testMdlJarDependency() throws IOException{
        // compile our java class
        File outputFolder = new File("build/java-jar");
        cleanCars(outputFolder.getPath());
        outputFolder.mkdirs();
        
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = javaCompiler.getStandardFileManager(null, null, null);
        File javaFile = new File(path+"/module/jarDependency/java/JavaDependency.java");
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(javaFile);
        CompilationTask task = javaCompiler.getTask(null, null, null, Arrays.asList("-d", "build/java-jar", "-sourcepath", "test-src"), null, compilationUnits);
        assertEquals(Boolean.TRUE, task.call());
        
        File jarFolder = new File(outputFolder, "com/redhat/ceylon/compiler/java/test/structure/module/jarDependency/java/1.0/");
        jarFolder.mkdirs();
        File jarFile = new File(jarFolder, "com.redhat.ceylon.compiler.java.test.structure.module.jarDependency.java-1.0.jar");
        // now jar it up
        JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(jarFile));
        ZipEntry entry = new ZipEntry("com/redhat/ceylon/compiler/java/test/structure/module/jarDependency/java/JavaDependency.class");
        outputStream.putNextEntry(entry);
        
        FileInputStream inputStream = new FileInputStream(javaFile);
        Util.copy(inputStream, outputStream);
        inputStream.close();
        outputStream.close();

        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-rep", outputFolder.getPath()), 
                (DiagnosticListener<? super FileObject>)null, 
                "module/jarDependency/ceylon/module.ceylon", "module/jarDependency/ceylon/Foo.ceylon");
        assertEquals(Boolean.TRUE, ceylonTask.call());
    }

    @Test
    public void testMdlMavenDependency() throws IOException{
        // Try to compile the ceylon module
        CeyloncTaskImpl ceylonTask = getCompilerTask(Arrays.asList("-out", destDir, "-rep", "mvn:http://repo1.maven.org/maven2"), 
                (DiagnosticListener<? super FileObject>)null, 
                "module/maven/module.ceylon", "module/maven/foo.ceylon");
        assertEquals(Boolean.TRUE, ceylonTask.call());
    }

    @Test
    public void testMdlSourceArchive() throws IOException{
        File sourceArchiveFile = getSourceArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6");
        sourceArchiveFile.delete();
        assertFalse(sourceArchiveFile.exists());

        // compile one file
        compile("module/single/module.ceylon");

        // make sure it was created
        assertTrue(sourceArchiveFile.exists());

        JarFile sourceArchive = new JarFile(sourceArchiveFile);
        assertEquals(1, countEntries(sourceArchive));

        ZipEntry moduleClass = sourceArchive.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/module.ceylon");
        assertNotNull(moduleClass);
        sourceArchive.close();

        // now compile another file
        compile("module/single/subpackage/Subpackage.ceylon");

        // MUST reopen it
        sourceArchive = new JarFile(sourceArchiveFile);
        assertEquals(2, countEntries(sourceArchive));

        ZipEntry subpackageClass = sourceArchive.getEntry("com/redhat/ceylon/compiler/java/test/structure/module/single/subpackage/Subpackage.ceylon");
        assertNotNull(subpackageClass);
        sourceArchive.close();
    }

    private int countEntries(JarFile jar) {
        int count = 0;
        Enumeration<JarEntry> entries = jar.entries();
        while(entries.hasMoreElements()){
            count++;
            entries.nextElement();
        }
        return count;
    }

    @Test
    public void testMdlSha1Signatures() throws IOException{
        File sourceArchiveFile = getSourceArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6");
        File sourceArchiveSignatureFile = new File(sourceArchiveFile.getPath()+".sha1");
        File moduleArchiveFile = getModuleArchive("com.redhat.ceylon.compiler.java.test.structure.module.single", "6.6.6");
        File moduleArchiveSignatureFile = new File(moduleArchiveFile.getPath()+".sha1");
        // cleanup
        sourceArchiveFile.delete();
        sourceArchiveSignatureFile.delete();
        moduleArchiveFile.delete();
        moduleArchiveSignatureFile.delete();
        // safety check
        assertFalse(sourceArchiveFile.exists());
        assertFalse(sourceArchiveSignatureFile.exists());
        assertFalse(moduleArchiveFile.exists());
        assertFalse(moduleArchiveSignatureFile.exists());

        // compile one file
        compile("module/single/module.ceylon");

        // make sure everything was created
        assertTrue(sourceArchiveFile.exists());
        assertTrue(sourceArchiveSignatureFile.exists());
        assertTrue(moduleArchiveFile.exists());
        assertTrue(moduleArchiveSignatureFile.exists());

        // check the signatures vaguely
        checkSha1(sourceArchiveSignatureFile);
        checkSha1(moduleArchiveSignatureFile);
    }

    private void checkSha1(File signatureFile) throws IOException {
        Assert.assertEquals(40, signatureFile.length());
        FileInputStream reader = new FileInputStream(signatureFile);
        byte[] bytes = new byte[40];
        Assert.assertEquals(40, reader.read(bytes));
        reader.close();
        char[] sha1 = new String(bytes, "ASCII").toCharArray();
        for (int i = 0; i < sha1.length; i++) {
            char c = sha1[i];
            Assert.assertTrue((c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F'));
        }
    }

    //
    // Attributes
    
    @Test
    public void testAtrClassAttribute(){
        compareWithJavaSource("attribute/ClassAttribute");
    }
    @Test
    public void testAtrClassAttributeWithInitializer(){
        compareWithJavaSource("attribute/ClassAttributeWithInitializer");
    }
    @Test
    public void testAtrClassAttributeGetter(){
        compareWithJavaSource("attribute/ClassAttributeGetter");
    }
    @Test
    public void testAtrClassAttributeGetterSetter(){
        compareWithJavaSource("attribute/ClassAttributeGetterSetter");
    }
    @Test
    public void testAtrClassVariable(){
        compareWithJavaSource("attribute/ClassVariable");
    }
    @Test
    public void testAtrClassVariableWithInitializer(){
        compareWithJavaSource("attribute/ClassVariableWithInitializer");
    }
    @Test
    public void testAtrInnerAttributeGetter(){
        compareWithJavaSource("attribute/InnerAttributeGetter");
    }
    @Test
    public void testAtrInnerAttributeGetterSetter(){
        compareWithJavaSource("attribute/InnerAttributeGetterSetter");
    }
    @Test
    public void testAtrInnerAttributeGetterLateInitialisation(){
        compareWithJavaSource("attribute/InnerAttributeGetterLateInitialisation");
    }
    @Test
    public void testAtrClassAttributeWithConflictingMethods(){
        compareWithJavaSource("attribute/ClassAttributeWithConflictingMethods");
    }
    @Test
    public void testAtrInnerAttributeGetterWithConflictingMethods(){
        compareWithJavaSource("attribute/InnerAttributeGetterWithConflictingMethods");
    }
    
    //
    // Classes
    
    @Test
    public void testKlsAbstractFormal(){
        compareWithJavaSource("klass/AbstractFormal");
    }
    @Test
    public void testKlsCaseTypes(){
        compareWithJavaSource("klass/CaseTypes");
    }
    @Test
    public void testKlsDefaultedInitializerParameter(){
        compareWithJavaSource("klass/DefaultedInitializerParameter");
    }
    @Test
    public void testKlsExtends(){
        compareWithJavaSource("klass/Extends");
    }
    @Test
    public void testKlsExtendsGeneric(){
        compareWithJavaSource("klass/ExtendsGeneric");
    }
    @Test
    public void testKlsInitializerParameter(){
        compareWithJavaSource("klass/InitializerParameter");
    }
    @Test
    public void testKlsInitializerVarargs(){
        compareWithJavaSource("klass/InitializerVarargs");
    }
    @Test
    public void testKlsInnerClass(){
        compareWithJavaSource("klass/InnerClass");
    }
    @Test
    public void testKlsInterface(){
        compareWithJavaSource("klass/Interface");
    }
    @Ignore("M2")
    @Test
    public void testKlsInterfaceWithConcreteMembers(){
        compareWithJavaSource("klass/InterfaceWithConcreteMembers");
    }
    @Test
    public void testKlsInterfaceWithMembers(){
        compareWithJavaSource("klass/InterfaceWithMembers");
    }
    @Test
    public void testKlsClass(){
        compareWithJavaSource("klass/Klass");
    }
    @Test
    public void testKlsKlassMethodTypeParams(){
        compareWithJavaSource("klass/KlassMethodTypeParams");
    }
    @Test
    public void testKlsKlassTypeParams(){
        compareWithJavaSource("klass/KlassTypeParams");
    }
    @Test
    public void testKlsKlassTypeParamsSatisfies(){
        compareWithJavaSource("klass/KlassTypeParamsSatisfies");
    }
    @Test
    public void testKlsKlassWithObjectMember(){
        compareWithJavaSource("klass/KlassWithObjectMember");
    }
    @Test
    public void testKlsLocalClass(){
        compareWithJavaSource("klass/LocalClass");
    }
    @Test
    public void testKlsPublicClass(){
        compareWithJavaSource("klass/PublicKlass");
    }
    @Test
    public void testKlsSatisfies(){
        compareWithJavaSource("klass/Satisfies");
    }
    @Test
    public void testKlsSatisfiesErasure(){
        compareWithJavaSource("klass/SatisfiesErasure");
    }
    @Test
    public void testKlsSatisfiesGeneric(){
        compareWithJavaSource("klass/SatisfiesGeneric");
    }
    @Test
    public void testKlsSatisfiesWithMembers(){
        compareWithJavaSource("klass/SatisfiesWithMembers");
    }
    @Test
    public void testKlsRefinedVarianceInheritance(){
        // See https://github.com/ceylon/ceylon-compiler/issues/319
        //compareWithJavaSource("klass/RefinedVarianceInheritance");
        compileAndRun("com.redhat.ceylon.compiler.java.test.structure.klass.rvi_run", "klass/RefinedVarianceInheritance.ceylon");
    }
    @Test
    public void testKlsRefinedVarianceInheritance2(){
        // See https://github.com/ceylon/ceylon-compiler/issues/354
        compareWithJavaSource("klass/RefinedVarianceInheritance2");
    }
    @Test
    public void testKlsVariance(){
        compareWithJavaSource("klass/Variance");
    }
    @Test
    public void testKlsObjectInMethod(){
        compareWithJavaSource("klass/ObjectInMethod");
    }
    @Test
    public void testKlsObjectInStatement(){
        compareWithJavaSource("klass/ObjectInStatement");
    }
    @Test
    public void testKlsInitializerObjectInStatement(){
        compareWithJavaSource("klass/InitializerObjectInStatement");
    }
    @Test
    public void testKlsKlassInStatement(){
        compareWithJavaSource("klass/KlassInStatement");
    }
    @Test
    public void testKlsInitializerKlassInStatement(){
        compareWithJavaSource("klass/InitializerKlassInStatement");
    }
    @Test
    public void testKlsObjectInGetter(){
        compareWithJavaSource("klass/ObjectInGetter");
    }
    @Test
    public void testKlsObjectInSetter(){
        compareWithJavaSource("klass/ObjectInSetter");
    }
    @Test
    public void testKlsClassInGetter(){
        compareWithJavaSource("klass/KlassInGetter");
    }
    @Test
    public void testKlsClassInSetter(){
        compareWithJavaSource("klass/KlassInSetter");
    }
    
    //
    // Methods
    
    @Test
    public void testMthLocalMethod(){
        compareWithJavaSource("method/LocalMethod");
    }
    @Test
    public void testMthMethod(){
        compareWithJavaSource("method/Method");
    }
    @Test
    public void testMthMethodErasure(){
        compareWithJavaSource("method/MethodErasure");
    }
    @Test
    public void testMthMethodTypeParams(){
        compareWithJavaSource("method/MethodTypeParams");
    }
    @Test
    public void testMthMethodWithDefaultParams(){
        compareWithJavaSource("method/MethodWithDefaultParams");
    }
    @Test
    public void testMthMethodWithLocalObject(){
        compareWithJavaSource("method/MethodWithLocalObject");
    }
    @Test
    public void testMthMethodWithParam(){
        compareWithJavaSource("method/MethodWithParam");
    }
    @Test
    public void testMthMethodWithVarargs(){
        compareWithJavaSource("method/MethodWithVarargs");
    }
    @Test
    public void testMthPublicMethod(){
        compareWithJavaSource("method/PublicMethod");
    }
    @Test
    public void testMthFunctionInStatement(){
        compareWithJavaSource("method/FunctionInStatement");
    }
    @Test
    public void testMthFunctionInGetter(){
        compareWithJavaSource("method/FunctionInGetter");
    }
    @Test
    public void testMthFunctionInSetter(){
        compareWithJavaSource("method/FunctionInSetter");
    }
    
    //
    // Toplevel
    
    @Test
    public void testTopToplevelAttribute(){
        compareWithJavaSource("toplevel/ToplevelAttribute");
    }
    @Test
    public void testTopToplevelAttributeShared(){
        compareWithJavaSource("toplevel/ToplevelAttributeShared");
    }
    @Test
    public void testTopToplevelGetter(){
        compareWithJavaSource("toplevel/ToplevelGetter");
    }
    @Test
    public void testTopToplevelGetterSetter(){
        compareWithJavaSource("toplevel/ToplevelGetterSetter");
    }
    @Test
    public void testTopToplevelMethods(){
        compareWithJavaSource("toplevel/ToplevelMethods");
    }
    @Test
    public void testTopToplevelMethodWithDefaultedParams(){
        compareWithJavaSource("toplevel/ToplevelMethodWithDefaultedParams");
    }
    @Test
    public void testTopToplevelObject(){
        compareWithJavaSource("toplevel/ToplevelObject");
    }
    @Test
    public void testTopToplevelObjectWithMembers(){
        compareWithJavaSource("toplevel/ToplevelObjectWithMembers");
    }
    @Test
    public void testTopToplevelObjectShared(){
        compareWithJavaSource("toplevel/ToplevelObjectShared");
    }
    @Test
    public void testTopToplevelObjectWithSupertypes(){
        compareWithJavaSource("toplevel/ToplevelObjectWithSupertypes");
    }
    @Test
    public void testTopToplevelVariable(){
        compareWithJavaSource("toplevel/ToplevelVariable");
    }
    @Test
    public void testTopToplevelVariableShared(){
        compareWithJavaSource("toplevel/ToplevelVariableShared");
    }
    
    //
    // Type
    
    @Test
    public void testTypBasicTypes(){
        compareWithJavaSource("type/BasicTypes");
    }
    @Test
    public void testTypBottom(){
        compareWithJavaSource("type/Bottom");
    }
    @Test
    public void testTypConversions(){
        compareWithJavaSource("type/Conversions");
    }
    @Test
    public void testTypOptionalType(){
        compareWithJavaSource("type/OptionalType");
    }
    @Test
    public void testTypSequenceType(){
        compareWithJavaSource("type/SequenceType");
    }
    
    //
    // import
    
    @Test
    public void testImpImportAliasAndWildcard(){
        compileImportedPackage();
        compareWithJavaSource("import_/ImportAliasAndWildcard");
    }
    
    private void compileImportedPackage() {
        compile("import_/pkg/C1.ceylon", "import_/pkg/C2.ceylon");
    }

    @Test
    public void testImpImportAttrSingle(){
        compileImportedPackage();
        compareWithJavaSource("import_/ImportAttrSingle");
    }

    @Test
    public void testImpImportMethodSingle(){
        compileImportedPackage();
        compareWithJavaSource("import_/ImportMethodSingle");
    }
    
    @Test
    public void testImpImportTypeSingle(){
        compileImportedPackage();
        compareWithJavaSource("import_/ImportTypeSingle");
    }
    
    @Test
    public void testImpImportTypeMultiple(){
        compileImportedPackage();
        compareWithJavaSource("import_/ImportTypeMultiple");
    }
    
    @Test
    public void testImpImportTypeAlias(){
        compileImportedPackage();
        compareWithJavaSource("import_/ImportTypeAlias");
    }
    
    @Test
    public void testImpImportWildcard(){
        compileImportedPackage();
        compareWithJavaSource("import_/ImportWildcard");
    }
    
    @Test
    public void testImpImportJavaRuntimeTypeSingle(){
        compareWithJavaSource("import_/ImportJavaRuntimeTypeSingle");
    }

    @Test
    public void testImpImportJavaRuntimeTypeWildcard(){
        compareWithJavaSource("import_/ImportJavaRuntimeTypeWildcard");
    }

    @Test
    public void testImpImportCeylonLanguageType(){
        compareWithJavaSource("import_/ImportCeylonLanguageType");
    }
}
