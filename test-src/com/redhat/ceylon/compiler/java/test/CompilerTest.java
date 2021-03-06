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
package com.redhat.ceylon.compiler.java.test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;

import junit.framework.Assert;

import org.junit.Before;

import com.redhat.ceylon.compiler.java.codegen.AbstractTransformer;
import com.redhat.ceylon.compiler.java.codegen.JavaPositionsRetriever;
import com.redhat.ceylon.compiler.java.tools.CeyloncFileManager;
import com.redhat.ceylon.compiler.java.tools.CeyloncTaskImpl;
import com.redhat.ceylon.compiler.java.tools.CeyloncTool;
import com.redhat.ceylon.compiler.java.util.RepositoryLister;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.zip.ZipFileIndex;

public abstract class CompilerTest {

    private final static String dir = "test-src";
    protected final static String destDir = "build/ceylon-cars";
    private final static String destCar = destDir + "/default/default.car";
    protected final static List<String> defaultOptions = Arrays.asList("-out", destDir, "-rep", destDir);

    protected final String path;

    public CompilerTest() {
        // for comparing with java source
        Package pakage = getClass().getPackage();
        String pkg = pakage == null ? "" : pakage.getName().replaceAll("\\.", Matcher.quoteReplacement(File.separator));
        path = dir + File.separator + pkg + File.separator;
    }

    protected CeyloncTool makeCompiler(){
        try {
            return new CeyloncTool();
        } catch (VerifyError e) {
            System.err.println("ERROR: Cannot run tests! Did you maybe forget to configure the -Xbootclasspath/p: parameter?");
            throw e;
        }
    }

    protected CeyloncFileManager makeFileManager(CeyloncTool compiler, DiagnosticListener<? super FileObject> diagnosticListener){
        return (CeyloncFileManager)compiler.getStandardFileManager(diagnosticListener, null, null);
	}
	
	protected void compareWithJavaSource(String name) {
		compareWithJavaSource(name+".src", name+".ceylon");
	}

	@Before
	public void cleanCars() {
	    cleanCars(destDir);
	}
	
    public void cleanCars(String repo) {
        File destFile = new File(repo);
        List<String> extensionsToDelete = Arrays.asList("");
        new RepositoryLister(extensionsToDelete).list(destFile, new RepositoryLister.Actions() {
            @Override
            public void doWithFile(File path) {
                path.delete();
            }

            public void exitDirectory(File path) {
                if (path.list().length == 0) {
                    path.delete();
                }
            }
        });
    }

    public static class CompilerError implements Comparable<CompilerError>{
        private final long lineNumber;
        private final String message;

        public CompilerError(long lineNumber, String message) {
            this.lineNumber = lineNumber;
            this.message = message;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (lineNumber ^ (lineNumber >>> 32));
            result = prime * result
                    + ((message == null) ? 0 : message.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CompilerError other = (CompilerError) obj;
            if (lineNumber != other.lineNumber)
                return false;
            if (message == null) {
                if (other.message != null)
                    return false;
            } else if (!message.equals(other.message))
                return false;
            return true;
        }
        
        public String toString() {
            return lineNumber + ": " + message;
        }

        @Override
        public int compareTo(CompilerError o) {
            long cmp = this.lineNumber - o.lineNumber;
            if (cmp == 0) {
                cmp = this.message.compareTo(o.message);
            }
            return cmp > 0 ? 1 : cmp < 0 ? -1 : 0;
        }
    }
    
    protected void assertErrors(String ceylon, CompilerError... expectedErrors) {
        // make a compiler task
        // FIXME: runFileManager.setSourcePath(dir);
        final TreeSet<CompilerError> actualErrors = new TreeSet<CompilerError>();
        
        CeyloncTaskImpl task = getCompilerTask(defaultOptions, new DiagnosticListener() {
            @Override
            public void report(Diagnostic diagnostic) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    actualErrors.add(new CompilerError(diagnostic.getLineNumber(),
                            diagnostic.getMessage(Locale.getDefault())));
                }
            }
        
        }, new String[] {ceylon+".ceylon"});

        // now compile it all the way
        Boolean success = task.call();

        Assert.assertFalse("Compilation succeeded", success);
        
        Assert.assertEquals("Errors different from expected", new TreeSet<CompilerError>(Arrays.asList(expectedErrors)), actualErrors);
        
    }
    
    protected void compareWithJavaSourceWithPositions(String name) {
        // make a compiler task
        // FIXME: runFileManager.setSourcePath(dir);
        CeyloncTaskImpl task = getCompilerTask(new String[] {name+".ceylon"});

        // grab the CU after we've completed it
        class Listener implements TaskListener{
            JCCompilationUnit compilationUnit;
            private String compilerSrc;
            private JavaPositionsRetriever javaPositionsRetriever = new JavaPositionsRetriever();

            @Override
            public void started(TaskEvent e) {
                AbstractTransformer.trackNodePositions(javaPositionsRetriever);
            }

            @Override
            public void finished(TaskEvent e) {
                if(e.getKind() == Kind.ENTER){
                    if(compilationUnit == null) {
                        compilationUnit = (JCCompilationUnit) e.getCompilationUnit();
                        // for some reason compilationUnit is full here in the listener, but empty as soon
                        // as the compile task is done. probably to clean up for the gc?
                        javaPositionsRetriever.retrieve(compilationUnit);
                        compilerSrc = normalizeLineEndings(javaPositionsRetriever.getJavaSourceCodeWithCeylonPositions());
                        AbstractTransformer.trackNodePositions(null);
                    }
                }
            }
        }
        Listener listener = new Listener();
        task.setTaskListener(listener);

        // now compile it all the way
        Boolean success = task.call();

        Assert.assertTrue("Compilation failed", success);

        // now look at what we expected
        String expectedSrc = normalizeLineEndings(readFile(new File(path, name+".src"))).trim();
        String compiledSrc = listener.compilerSrc.trim();
        Assert.assertEquals("Source code differs", expectedSrc, compiledSrc);
    }

    protected void compareWithJavaSourceWithLines(String name) {
        // make a compiler task
        // FIXME: runFileManager.setSourcePath(dir);
        CeyloncTaskImpl task = getCompilerTask(new String[] {name+".ceylon"});

        // grab the CU after we've completed it
        class Listener implements TaskListener{
            JCCompilationUnit compilationUnit;
            private String compilerSrc;
            private JavaPositionsRetriever javaPositionsRetriever = new JavaPositionsRetriever();

            @Override
            public void started(TaskEvent e) {
            }

            @Override
            public void finished(TaskEvent e) {
                if(e.getKind() == Kind.ENTER){
                    if(compilationUnit == null) {
                        compilationUnit = (JCCompilationUnit) e.getCompilationUnit();
                        // for some reason compilationUnit is full here in the listener, but empty as soon
                        // as the compile task is done. probably to clean up for the gc?
                        javaPositionsRetriever.retrieve(compilationUnit);
                        compilerSrc = normalizeLineEndings(javaPositionsRetriever.getJavaSourceCodeWithCeylonLines());
                        AbstractTransformer.trackNodePositions(null);
                    }
                }
            }
        }
        Listener listener = new Listener();
        task.setTaskListener(listener);

        // now compile it all the way
        Boolean success = task.call();

        Assert.assertTrue("Compilation failed", success);

        // now look at what we expected
        String expectedSrc = normalizeLineEndings(readFile(new File(path, name+".src"))).trim();
        String compiledSrc = listener.compilerSrc.trim();
        Assert.assertEquals("Source code differs", expectedSrc, compiledSrc);
    }

    protected void compareWithJavaSource(String java, String... ceylon) {
        // make a compiler task
        // FIXME: runFileManager.setSourcePath(dir);
        CeyloncTaskImpl task = getCompilerTask(ceylon);

        // grab the CU after we've completed it
        class Listener implements TaskListener{
            JCCompilationUnit compilationUnit;
            private String compilerSrc;
            @Override
            public void started(TaskEvent e) {
            }

            @Override
            public void finished(TaskEvent e) {
                if(e.getKind() == Kind.ENTER){
                    if(compilationUnit == null) {
                        compilationUnit = (JCCompilationUnit) e.getCompilationUnit();
                        // for some reason compilationUnit is full here in the listener, but empty as soon
                        // as the compile task is done. probably to clean up for the gc?
                        compilerSrc = normalizeLineEndings(compilationUnit.toString());
                    }
                }
            }
        }
        Listener listener = new Listener();
        task.setTaskListener(listener);

        // now compile it all the way
        Boolean success = task.call();

        Assert.assertTrue("Compilation failed", success);

        // now look at what we expected
        String expectedSrc = normalizeLineEndings(readFile(new File(path, java))).trim();
        String compiledSrc = listener.compilerSrc.trim();
        Assert.assertEquals("Source code differs", expectedSrc, compiledSrc);
    }

    private String readFile(File file) {
        try{
            Reader reader = new FileReader(file);
            StringBuilder strbuf = new StringBuilder();
            try{
                char[] buf = new char[1024];
                int read;
                while((read = reader.read(buf)) > -1)
                    strbuf.append(buf, 0, read);
            }finally{
                reader.close();
            }
            return strbuf.toString();
        }catch(IOException x){
            throw new RuntimeException(x);
        }
    }

    private String normalizeLineEndings(String txt) {
        String result = txt.replaceAll("\r\n", "\n"); // Windows
        result = result.replaceAll("\r", "\n"); // Mac (OS<=9)
        return result;
    }

    protected void compile(String... ceylon) {
        Boolean success = getCompilerTask(ceylon).call();
        Assert.assertTrue(success);
    }

    protected void compileAndRun(String main, String... ceylon) {
        compile(ceylon);
        try{
            // make sure we load the stuff from the Car
            File car = new File(destCar);
            @SuppressWarnings("deprecation")
            URL url = car.toURL();
            
            NonCachingURLClassLoader loader = new NonCachingURLClassLoader(new URL[] { url  });
            java.lang.Class<?> klass = java.lang.Class.forName(main, true, loader);
            Method m = klass.getMethod(klass.getSimpleName());
            m.invoke(null);
            
            loader.clearCache();
        }catch(Exception x){
            throw new RuntimeException(x);
        }
    }

    public static class NonCachingURLClassLoader extends URLClassLoader {

        public NonCachingURLClassLoader(URL[] urls) {
            super(urls);
        }

        public void clearCache() {
            try {
                Class<?> klass = java.net.URLClassLoader.class;
                Field ucp = klass.getDeclaredField("ucp");
                ucp.setAccessible(true);
                Object sunMiscURLClassPath = ucp.get(this);
                Field loaders = sunMiscURLClassPath.getClass().getDeclaredField("loaders");
                loaders.setAccessible(true);
                Object collection = loaders.get(sunMiscURLClassPath);
                for (Object sunMiscURLClassPathJarLoader : ((Collection<?>) collection).toArray()) {
                    try {
                        Field loader = sunMiscURLClassPathJarLoader.getClass().getDeclaredField("jar");
                        loader.setAccessible(true);
                        Object jarFile = loader.get(sunMiscURLClassPathJarLoader);
                        ((JarFile) jarFile).close();
                    } catch (Throwable t) {
                        // not a JAR loader?
                        t.printStackTrace();
                    }
                }
            } catch (Throwable t) {
                // Something's wrong
                t.printStackTrace();
            }
            return;
        }
    }

    protected CeyloncTaskImpl getCompilerTask(String... sourcePaths){
        return getCompilerTask(defaultOptions, null, sourcePaths);
    }

    protected CeyloncTaskImpl getCompilerTask(List<String> defaultOptions, String... sourcePaths){
        return getCompilerTask(defaultOptions, null, sourcePaths);
    }

    protected CeyloncTaskImpl getCompilerTask(List<String> defaultOptions, DiagnosticListener<? super FileObject> diagnosticListener, 
            String... sourcePaths){
        // make sure we get a fresh jar cache for each compiler run
        ZipFileIndex.clearCache();
        java.util.List<File> sourceFiles = new ArrayList<File>(sourcePaths.length);
        for(String file : sourcePaths){
            sourceFiles.add(new File(path, file));
        }

        CeyloncTool runCompiler = makeCompiler();
        CeyloncFileManager runFileManager = makeFileManager(runCompiler, diagnosticListener);

        // make sure the destination repo exists
        new File(destDir).mkdirs();

        List<String> options = new LinkedList<String>();
        options.addAll(defaultOptions);
        options.addAll(Arrays.asList("-src", getSourcePath(), "-verbose:ast,code"));
        Iterable<? extends JavaFileObject> compilationUnits1 =
                runFileManager.getJavaFileObjectsFromFiles(sourceFiles);
        return (CeyloncTaskImpl) runCompiler.getTask(null, runFileManager, diagnosticListener, 
                options, null, compilationUnits1);
    }

    protected String getSourcePath() {
        return dir;
    }
}
