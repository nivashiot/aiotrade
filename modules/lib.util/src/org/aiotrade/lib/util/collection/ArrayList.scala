/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ArrayList.scala 19223 2009-10-22 10:43:02Z malayeri $


package org.aiotrade.lib.util.collection

import scala.collection.generic._
import scala.collection.mutable._

/** An implementation of the <code>Buffer</code> class using an array to
 *  represent the assembled sequence internally. Append, update and random
 *  access take constant time (amortized time). Prepends and removes are
 *  linear in the buffer size.
 *
 *  serialver -classpath ~/myapps/scala/lib/scala-library.jar:./ org.aiotrade.lib.util.collection.ArrayList
 *  
 *  @author  Matthias Zenger
 *  @author  Martin Odersky
 *  @version 2.8
 *  @since   1
 */
@serializable @SerialVersionUID(1529165946227428979L)
class ArrayList[A](override protected val initialSize: Int)
  extends Buffer[A] 
     with GenericTraversableTemplate[A, ArrayList]
     with BufferLike[A, ArrayList[A]]
     with IndexedSeqLike[A, ArrayList[A]]
     with Builder[A, ArrayList[A]]
     with ResizableArray[A] {

  override def companion: GenericCompanion[ArrayList] = ArrayList

  import scala.collection.Traversable

  def this() = this(16)

  def clear() { reduceToSize(0) }

  override def sizeHint(len: Int) {
    if (len > size && len >= 1) {
      val newarray = new Array[AnyRef](len min 1)
      Array.copy(array, 0, newarray, 0, size0)
      array = newarray
    }
  }
  
  /** Appends a single element to this buffer and returns
   *  the identity of the buffer. It takes constant time.
   *
   *  @param elem  the element to append.
   */
  def +=(elem: A): this.type = {
    ensureSize(size0 + 1)
    array(size0) = elem.asInstanceOf[AnyRef]
    size0 += 1
    this
  }

  /** Appends a number of elements provided by an iterable object
   *  via its <code>iterator</code> method. The identity of the
   *  buffer is returned.
   *
   *  @param iter  the iterfable object.
   *  @return      the updated buffer.
   */
  override def ++=(iter: Traversable[A]): this.type = iter match {
    case v: IndexedSeq[_] =>
      val n = v.length
      ensureSize(size0 + n)
      v.copyToArray(array.asInstanceOf[scala.Array[Any]], size0, n)
      size0 += n
      this
    case _ =>
      super.++=(iter)
  }

  /** Prepends a single element to this buffer and return
   *  the identity of the buffer. It takes time linear in 
   *  the buffer size.
   *
   *  @param elem  the element to append.
   *  @return      the updated buffer. 
   */
  def +=:(elem: A): this.type = {
    ensureSize(size0 + 1)
    copy(0, 1, size0)
    array(0) = elem.asInstanceOf[AnyRef]
    size0 += 1
    this
  }
   
  /** Prepends a number of elements provided by an iterable object
   *  via its <code>iterator</code> method. The identity of the
   *  buffer is returned.
   *
   *  @param iter  the iterable object.
   *  @return      the updated buffer.
   */
  override def ++=:(iter: Traversable[A]): this.type = { insertAll(0, iter); this }
  
  /** Inserts new elements at the index <code>n</code>. Opposed to method
   *  <code>update</code>, this method will not replace an element with a
   *  one. Instead, it will insert a new element at index <code>n</code>.
   *
   *  @param n     the index where a new element will be inserted.
   *  @param iter  the iterable object providing all elements to insert.
   *  @throws Predef.IndexOutOfBoundsException if <code>n</code> is out of bounds.
   */
  def insertAll(n: Int, seq: Traversable[A]) {
    if ((n < 0) || (n > size0))
      throw new IndexOutOfBoundsException(n.toString)
    val xs = seq.toList
    val len = xs.length
    ensureSize(size0 + len)
    copy(n, n + len, size0 - n)
    xs.copyToArray(array.asInstanceOf[scala.Array[Any]], n)
    size0 += len
  }
  
  /** Removes the element on a given index position. It takes time linear in
   *  the buffer size.
   *
   *  @param n  the index which refers to the first element to delete.
   *  @param count   the number of elemenets to delete
   *  @throws Predef.IndexOutOfBoundsException if <code>n</code> is out of bounds.
   */
  override def remove(n: Int, count: Int) {
    if ((n < 0) || (n >= size0) && count > 0)
      throw new IndexOutOfBoundsException(n.toString)
    copy(n + count, n, size0 - (n + count))
    size0 -= count
  }

  /** Removes the element on a given index position
   *  
   *  @param n  the index which refers to the element to delete.
   *  @return  The element that was formerly at position `n`
   */
  def remove(n: Int): A = {
    val result = apply(n)
    remove(n, 1)
    result
  }

  /** Return a clone of this buffer.
   *
   *  @return an <code>ArrayList</code> with the same elements.
   */
  override def clone(): ArrayList[A] = new ArrayList[A] ++= this

  def result: ArrayList[A] = this

  /** Defines the prefix of the string representation.
   */
  override def stringPrefix: String = "ArrayList"
}

/** Factory object for <code>ArrayList</code> class.
 *
 *  @author  Martin Odersky
 *  @version 2.8
 *  @since   2.8
 */
object ArrayList extends SeqFactory[ArrayList] {
  implicit def canBuildFrom[A]: CanBuildFrom[Coll, A, ArrayList[A]] = new GenericCanBuildFrom[A]
  def newBuilder[A]: Builder[A, ArrayList[A]] = new ArrayList[A]
}
