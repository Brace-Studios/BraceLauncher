package dev.dubhe.brace.launcher;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Launcher {
    public static final Path ROOT = new File("").toPath();
    public static final Path LIBRARIES_PATH = ROOT.resolve("libraries");

    public static void main(String[] args) {
        System.out.println("Downloading library, please wait...");
        if (!LIBRARIES_PATH.toFile().isDirectory()) LIBRARIES_PATH.toFile().mkdir();
        String gson = "gson-2.8.9.jar";
        if (!LIBRARIES_PATH.resolve(gson).toFile().isFile()) {
            String gson_url = "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar";
            downloadByNIO(gson_url, LIBRARIES_PATH.toAbsolutePath().toString(), gson);
        }
        if (args.length < 1) return;
        loadLibraries(args[0]);
        if (args.length >= 2) loadMain(args[0], args[1]);
        else loadMain(args[0]);
    }

    public static long copy(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[4096];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    private static void loadMain(String jar) {
        loadMain(jar, System.getProperty("java.home") + "/bin/java.exe");
    }

    private static void loadMain(String jar, String java) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(java, "-jar", jar);
            Process process = processBuilder.start();
            new Console(process).start();
            copy(process.getInputStream(), System.out);
            copy(process.getErrorStream(), System.err);
            if (process.waitFor() != 0) {
                if (process.exitValue() == 1)
                    System.err.println("命令执行失败!");
            } else {
                System.exit(0);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void loadLibraries(String jar) {
        try (JarFile file = new JarFile(ROOT.resolve(jar).toFile())) {
            URL[] urls = new URL[]{LIBRARIES_PATH.resolve("gson-2.8.9.jar").toFile().toURI().toURL()};
            try (URLClassLoader classLoader = new URLClassLoader(urls)) {
                Class<?> clazz = classLoader.loadClass("com.google.gson.Gson");
                Method fromJson = clazz.getMethod("fromJson", Reader.class, Class.class);
                Object gson = clazz.getDeclaredConstructor().newInstance();
                for (Iterator<JarEntry> it = file.entries().asIterator(); it.hasNext(); ) {
                    JarEntry entry = it.next();
                    if ("libraries.json".equals(entry.getName())) {
                        try (
                                InputStream in = file.getInputStream(entry);
                                InputStreamReader reader = new InputStreamReader(in)
                        ) {
                            Libraries libraries = (Libraries) fromJson.invoke(gson, reader, Libraries.class);
                            List<String> maven_source = libraries.maven_source;
                            List<Libraries.Library> libs = libraries.libraries;
                            for (Libraries.Library lib : libs) {
                                String name = "%s-%s.jar".formatted(lib.name, lib.version);
                                if (LIBRARIES_PATH.resolve(name).toFile().isFile()) continue;
                                for (String source : maven_source) {
                                    if (!source.endsWith("/")) source += "/";
                                    String url = "%s%s/%s/%s/%s".formatted(source, lib.group.replace('.', '/'), lib.name, lib.version, name);
                                    if (downloadByNIO(url, LIBRARIES_PATH.toAbsolutePath().toString(), name)) break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException | ClassNotFoundException | NoSuchMethodException | InstantiationException |
                 IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public static boolean downloadByNIO(String url, String saveDir, String fileName) {
        File file = new File(saveDir, fileName);
        file.getParentFile().mkdirs();
        try (
                ReadableByteChannel rbc = Channels.newChannel(new URL(url).openStream());
                FileOutputStream fos = new FileOutputStream(file);
                FileChannel foutc = fos.getChannel();
        ) {
            System.out.printf("Downloading: %s\n", url);
            foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
            return true;
        } catch (FileNotFoundException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class Libraries {
        List<String> maven_source = new Vector<>();
        List<Library> libraries = new Vector<>();

        public static class Library {
            String group = "";
            String name = "";
            String version = "";
        }
    }
}
