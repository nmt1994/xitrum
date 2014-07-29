package xitrum.util

import java.io.File
import java.nio.file.Path
import scala.collection.mutable.{Map => MMap}

/**
 * This utility is useful for hot reloading .class files in a directory during
 * development.
 *
 * @param dirs Directories to search for .class files, example: Seq("target/scala-2.11/classes")
 */
class ClassFileLoader(dirs: Seq[Path]) extends ClassLoader(Thread.currentThread.getContextClassLoader) {
  // http://docs.oracle.com/javase/7/docs/api/java/lang/ClassLoader.html
  //
  // The ClassLoader class uses a delegation model to search for classes and
  // resources. Each instance of ClassLoader has an associated parent class
  // loader. When requested to find a class or resource, a ClassLoader instance
  // will delegate the search for the class or resource to its parent class
  // loader before attempting to find the class or resource itself.

  // Need to cache because calling defineClass twice will cause exception
  protected val cache = MMap[String, Class[_]]()

  //override def loadClass(name: String): Class[_] = {
  //  findClass(name)
  //}

  override def findClass(name: String): Class[_] = synchronized {
    cache.get(name) match {
      case Some(klass) =>
        klass

      case None =>
        classNameToFilePath(name) match {
          case Some(path) =>
            val bytes   = Loader.bytesFromFile(path)
            val klass   = defineClass(name, bytes, 0, bytes.length)
            cache(name) = klass
            klass

          case None =>
            //getParent.loadClass(name)
            fallback.loadClass(name)
        }
    }
  }

  //----------------------------------------------------------------------------

  /**
   * Fallback ClassLoader used when the class file couldn't be found or when
   * class name matches ignorePattern. Default: Thread.currentThread.getContextClassLoader.
   */
  protected def fallback: ClassLoader = Thread.currentThread.getContextClassLoader

  /** @return None to use the fallback ClassLoader */
  protected def classNameToFilePath(name: String): Option[String] = {
    // Scala 2.10 only has #toString, #regex is from Scala 2.11
    if (!ignorePattern.toString.isEmpty && ignorePattern.findFirstIn(name).isDefined) {
      None
    } else {
      val relPath = name.replaceAllLiterally(".", File.separator) + ".class"
      val paths   = dirs.map(_ + File.separator + relPath)
      paths.find(new File(_).exists)
    }
  }

  /**
   * If necessary, override this method to ignore reloading classes that may
   * cause exceptions if the classes are reloaded, like:
   * java.lang.ClassCastException: demos.action.Article cannot be cast to demos.action.Article
   */
  protected def ignorePattern = "".r
}
