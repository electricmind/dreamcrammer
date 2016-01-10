package ru.wordmetrix.dreamcrammer.db

//#package scala

object OptionData {

  import scala.language.implicitConversions

  implicit def option2Iterable[A](xo: OptionData[A]): Iterable[A] = xo.toList
  def apply[A](x: A): OptionData[A] = if (x == null) NoData else SomeData(x)
  def empty[A] : OptionData[A] = NoData
}

sealed abstract class OptionData[+A] extends Product with Serializable {
  self =>

  def isEmpty: Boolean
  def isDefined: Boolean = !isEmpty

  def get: A
  @inline final def getOrElse[B >: A](default: => B): B =
    if (isEmpty) default else this.get

  @inline final def map[B](f: A => B): OptionData[B] =
    if (isEmpty) NoData else SomeData(f(this.get))

  @inline final def fold[B](ifEmpty: => B)(f: A => B): B =
    if (isEmpty) ifEmpty else f(this.get)

  @inline final def flatMap[B](f: A => OptionData[B]): OptionData[B] =
    if (isEmpty) NoData else f(this.get)

  def flatten[B](implicit ev: A <:< OptionData[B]): OptionData[B] =
    if (isEmpty) NoData else ev(this.get)

  @inline final def filter(p: A => Boolean): OptionData[A] =
    if (isEmpty || p(this.get)) this else NoData

  @inline final def filterNot(p: A => Boolean): OptionData[A] =
    if (isEmpty || !p(this.get)) this else NoData

  final def nonEmpty = isDefined

  @inline final def withFilter(p: A => Boolean): WithFilter = new WithFilter(p)

  class WithFilter(p: A => Boolean) {
    def map[B](f: A => B): OptionData[B] = self filter p map f
    def flatMap[B](f: A => OptionData[B]): OptionData[B] = self filter p flatMap f
    def foreach[U](f: A => U): Unit = self filter p foreach f
    def withFilter(q: A => Boolean): WithFilter = new WithFilter(x => p(x) && q(x))
  }

  @inline final def exists(p: A => Boolean): Boolean =
    !isEmpty && p(this.get)

  @inline final def forall(p: A => Boolean): Boolean = isEmpty || p(this.get)

  @inline final def foreach[U](f: A => U) {
    if (!isEmpty) f(this.get)
  }

  @inline final def collect[B](pf: PartialFunction[A, B]): OptionData[B] =
    if (!isEmpty && pf.isDefinedAt(this.get)) SomeData(pf(this.get)) else NoData

  @inline final def orElse[B >: A](alternative: => OptionData[B]): OptionData[B] =
    if (isEmpty) alternative else this

  def iterator: Iterator[A] =
    if (isEmpty) collection.Iterator.empty else collection.Iterator.single(this.get)

  def toList: List[A] =
    if (isEmpty) List() else new ::(this.get, Nil)

  @inline final def toRight[X](left: => X) =
    if (isEmpty) Left(left) else Right(this.get)

  @inline final def toLeft[X](right: => X) =
    if (isEmpty) Right(right) else Left(this.get)
}

final case class SomeData[+A](x: A) extends OptionData[A] {
  def isEmpty = false
  def get = x
}


case object NoData extends OptionData[Nothing] {
  def isEmpty = true
  def get = throw new NoSuchElementException("NoData.get")
}

case object NeverData extends OptionData[Nothing] {
  def isEmpty = true
  def get = throw new NoSuchElementException("NoData.get")
}
