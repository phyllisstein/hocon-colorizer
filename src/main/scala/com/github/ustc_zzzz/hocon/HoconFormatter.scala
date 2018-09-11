package com.github.ustc_zzzz.hocon


import scala.scalajs.js._

/**
  * @author ustc_zzzz
  */
object HoconFormatter {

  import com.github.ustc_zzzz.hocon.HoconObjects._
  import com.github.ustc_zzzz.hocon.vscode.facade.VSCodeLanguageServer.TextEdit

  case class IndentOptions(builder: Array[TextEdit], posAt: Int => Dynamic, tabSpace: String)

  def format(element: Element, options: IndentOptions, indent: Int = 0): Unit = element match {
    case Root(start, rootElement, end) =>
      formatSpaces(start, indent, options, headSpacesMultiline = "")
      format(rootElement, options, indent - 1)
      formatSpaces(end, indent, options)
    case RootObject(start, elements) =>
      formatSpaces(start, indent + 1, options)
      for ((head, tail, end) <- elements) {
        format(head, options, indent + 1)
        for ((SepWithSpaces(prefix, suffix), part) <- tail) {
          formatSpaces(prefix, suffix, indent + 1, options, "")
          format(part, options, indent + 1)
        }
        format(end, options, indent)
      }
    case Object(start, elements) =>
      var diff = 0
      if (formatSpaces(start, indent + 1, options)) diff = 1
      for ((head, tail, end) <- elements) {
        format(head, options, indent + diff)
        for ((SepWithSpaces(prefix, suffix), part) <- tail) {
          if (formatSpaces(prefix, suffix, indent + 1, options, "")) diff = 1
          format(part, options, indent + diff)
        }
        format(end, options, indent)
      }
    case List(start, elements) =>
      var diff = 0
      if (formatSpaces(start, indent + 1, options)) diff = 1
      for ((head, tail, end) <- elements) {
        format(head, options, indent + diff)
        for ((SepWithSpaces(prefix, suffix), part) <- tail) {
          if (formatSpaces(prefix, suffix, indent + 1, options, "")) diff = 1
          format(part, options, indent + diff)
        }
        format(end, options, indent)
      }
    case ObjectElement(_, (SepWithSpaces(prefix, suffix), value)) =>
      formatSpaces(prefix, suffix, indent, options, " ")
      format(value, options, indent)
    case Inclusion(spaces, _) =>
      formatSpaces(spaces, options.builder, options.posAt, " ")
    case ListElement(value) =>
      format(value, options, indent)
    case Concat(elements, noString) =>
      val last = elements.size - 1
      if (last >= 0) {
        for (i <- 0 until last) elements(i) match {
          case spaces: Spaces => if (noString) formatSpaces(spaces, options.builder, options.posAt, " ")
          case otherElement => format(otherElement, options, indent)
        }
        elements(last) match { // drop trailing spaces
          case spaces: Spaces => formatSpaces(spaces, options.builder, options.posAt, "")
          case otherElement => format(otherElement, options, indent)
        }
      }
    case SepWithSpaces(prefix, suffix) =>
      formatSpaces(prefix, suffix, indent, options, "")
    case _ => ()
  }

  private def formatSpaces(start: SpacesMultiline, suffix: Option[(Sep, SpacesMultiline)], indent: Int,
                           options: IndentOptions, spaces: String): scala.Boolean = (suffix: @unchecked) match {
    case None =>
      formatSpaces(start, indent, options, headSpaces = spaces)
    case Some((Sep(","), end)) =>
      formatSpaces(start, indent, options) || formatSpaces(end, indent, options, headSpaces = " ")
    case Some((Sep(":"), end)) =>
      formatSpaces(start, indent, options) || formatSpaces(end, indent, options, headSpaces = " ")
    case Some((Sep("="), end)) =>
      formatSpaces(start, indent, options, headSpaces = " ") || formatSpaces(end, indent, options, headSpaces = " ")
    case Some((Sep("+="), end)) =>
      formatSpaces(start, indent, options, headSpaces = " ") || formatSpaces(end, indent, options, headSpaces = " ")
    // other cases should not occur
  }

  private def formatSpaces(spaces: SpacesMultiline, indent: Int, options: IndentOptions,
                           headSpaces: String = "", headSpacesMultiline: String = " "): scala.Boolean = spaces match {
    case SpacesMultiline(Seq(head)) =>
      formatSpaces(head, options.builder, (headSpaces, headSpaces), options.posAt)
      false // subsequent elements should not be indented
    case SpacesMultiline(seq) if seq.nonEmpty =>
      val tailSpaces = options.tabSpace * indent
      formatSpaces(seq.head, options.builder, ("", headSpacesMultiline), options.posAt)
      seq.tail.foreach(formatSpaces(_, options.builder, (tailSpaces, tailSpaces), options.posAt))
      true // subsequent elements should be indented
    // other cases should not occur
  }

  private def formatSpaces(spaces: Spaces, builder: Array[TextEdit],
                           positionAt: Int => Dynamic, target: String): Unit = spaces match {
    case Spaces(start, value, end) => if (value != target) {
      builder.push(TextEdit.replace(Dictionary("start" -> positionAt(start), "end" -> positionAt(end)), target))
    }
  }

  private def formatSpaces(spaces: (Option[Spaces], Int, Option[Comment]), builder: Array[TextEdit],
                           spacesBeforeComment: (String, String), posAt: Int => Dynamic): Unit = spaces match {
    case (None, i, None) => builder.push(TextEdit.insert(posAt(i), spacesBeforeComment._1))
    case (Some(s), _, None) => formatSpaces(s, builder, posAt, spacesBeforeComment._1)
    case (o, i, Some(c)) =>
      o match {
        case None => builder.push(TextEdit.insert(posAt(i), spacesBeforeComment._2))
        case Some(s) => formatSpaces(s, builder, posAt, spacesBeforeComment._2)
      }
      c match {
        case Comment(p, None, _) => builder.push(TextEdit.insert(posAt(i + p.length), " "))
        case Comment(_, Some(Spaces(start, value, end)), _) => if (value != " ") {
          val range = Dictionary("start" -> posAt(start), "end" -> posAt(end))
          builder.push(TextEdit.replace(range, " "))
        }
      }
  }
}
