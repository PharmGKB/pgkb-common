package org.pharmgkb.common.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * JUnit test for {@link PathUtils}.
 *
 * @author Mark Woon
 */
class PathUtilsTest {


  @Test
  void testGetFilename() {

    assertEquals("foo.xml", PathUtils.getFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo.xml", PathUtils.getFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getFilename(Paths.get("/a/dir/for/foo")));

    IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
        () -> PathUtils.getFilename(Paths.get("/")));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains("no filename"), ex.getMessage());
  }

  @Test
  void testGetFileExtension() {

    assertEquals("ai", PathUtils.getFileExtension(Paths.get("/a/dir/foo.ai")));
    assertEquals("asdfasdf", PathUtils.getFileExtension(Paths.get("/a/dir/foo.asdfasdf")));
    assertEquals("zip", PathUtils.getFileExtension(Paths.get("/a/dir/foo.xml.zip")));
    assertNull(PathUtils.getFileExtension(Paths.get("/a/dir/foo")));
    assertNull(PathUtils.getFileExtension(Paths.get("/a/dir/foo.")));
    assertNull(PathUtils.getFileExtension(Paths.get("/a/.gitignore")));
  }

  @Test
  void testGetBaseFilename() {

    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo")));
    assertEquals("foo.bar", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.bar.xml")));
    assertEquals(".gitignore", PathUtils.getBaseFilename(Paths.get("/a/.gitignore")));
  }


  @Test
  void testGetPathToResourceGood() {

    assertTrue(Files.exists(PathUtils.getPathToResource("org/pharmgkb/common/util/PathUtilsTest.txt")));
    assertTrue(Files.exists(PathUtils.getPathToResource("/org/pharmgkb/common/util/PathUtilsTest.txt")));
    assertTrue(Files.exists(PathUtils.getPathToResource(PathUtils.class, "PathUtilsTest.txt")));
  }

  @Test
  void testGetPathToResourceWithNullClassThrows() {
    // a null clz must fail with a clear message naming the problem argument, not a raw NPE from
    // clz.getResource(filename) whose message (if any) talks about Class.getResource() instead
    NullPointerException ex = assertThrows(NullPointerException.class,
        () -> PathUtils.getPathToResource(null, "foo.txt"));
    assertEquals("clz is null", ex.getMessage());
  }

  @Test
  void testGetPathToResourceInJar() {
    // resolve a resource that's packaged in a jar dependency (junit-jupiter-api), not on a loose classpath dir
    assertTrue(Files.exists(PathUtils.getPathToResource("org/junit/jupiter/api/Test.class")));
    // do it twice - the filesystem should already be registered the second time around
    assertTrue(Files.exists(PathUtils.getPathToResource("org/junit/jupiter/api/Test.class")));
  }

  @Test
  void testGetPathToResourceBad() {

    Assertions.assertThrows(IllegalArgumentException.class, () ->
        PathUtils.getPathToResource("org/pharmgkb/common/util/nonexistent.tsv"));
  }

  @Test
  void testGetPathToResourceInJarConcurrently() throws Exception {
    // resolve a resource in a jar that hasn't been touched by any other test yet, from multiple threads at once,
    // to exercise the race between FileSystems.getFileSystem() and FileSystems.newFileSystem()
    String resource = "org/hamcrest/Matcher.class";
    int threadCount = 8;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      List<Future<Path>> futures = IntStream.range(0, threadCount)
          .<Future<Path>>mapToObj(i -> executor.submit(() -> {
            barrier.await();
            return PathUtils.getPathToResource(resource);
          }))
          .collect(Collectors.toList());
      for (Future<Path> future : futures) {
        assertTrue(Files.exists(future.get()));
      }
    } finally {
      executor.shutdown();
    }
  }
}
