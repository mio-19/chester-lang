package chester.parser

import fastparse.*
import NoWhitespace.*
import chester.error.*
import chester.syntax.concrete.*
import chester.utils.StringIndex
import chester.utils.parse.*

import java.nio.file.{Files, Paths}
import scala.util.*
import chester.syntax.IdentifierRules.*

import scala.collection.immutable

case class ParserInternal(fileName: String, ignoreLocation: Boolean = false, defaultIndexer: Option[StringIndex] = None, linesOffset: Integer = 0, posOffset: Integer = 0)(implicit p: P[?]) {
  if (linesOffset != 0) require(posOffset != 0)
  if (posOffset != 0) require(linesOffset != 0)
  require(posOffset >= 0)
  require(linesOffset >= 0)

  def nEnd: P[Unit] = P("\n" | End)

  @deprecated("comment is lost")
  def comment: P[Unit] = P("//" ~ CharPred(_ != '\n').rep ~ nEnd)

  def commentOneLine: P[Comment] = PwithMeta("//" ~ CharPred(_ != '\n').rep.! ~ ("\n" | End)).map { case (content, meta) =>
    Comment(content, CommentType.OneLine, meta.flatMap(_.sourcePos))
  }

  def allComment: P[Comment] = P(commentOneLine)

  def simpleDelimiter: P[Unit] = P(CharsWhileIn(" \t\r\n"))

  @deprecated("comment is lost")
  def delimiter: P[Unit] = P((simpleDelimiter | comment).rep)

  def delimiter1: P[Vector[Comment]] = P((simpleDelimiter.map(x => Vector()) | allComment.map(Vector(_))).rep).map(_.flatten.toVector)

  @deprecated("comment is lost")
  def lineEnding: P[Unit] = P(comment | (CharsWhileIn(" \t\r").? ~ ("\n" | End)))

  def lineEnding1: P[Vector[Comment]] = P(commentOneLine.map(Vector(_)) | (CharsWhileIn(" \t\r").? ~ nEnd).map(x => Vector()))

  def lineNonEndingSpace: P[Unit] = P((CharsWhileIn(" \t\r")))

  @deprecated("comment is lost")
  def maybeSpace: P[Unit] = P(delimiter.?)

  def maybeSpace1: P[Vector[Comment]] = P(delimiter1.?.map(_.toVector.flatten))

  def simpleId: P[String] = P((CharacterPred(identifierFirst).rep(1) ~ CharacterPred(identifierRest).rep).!)

  def id: P[String] = operatorId | simpleId

  def operatorId: P[String] = P((CharacterPred(operatorIdentifierFirst).rep(1) ~ CharacterPred(operatorIdentifierRest).rep).!)

  def begin: P[Int] = Index

  def end: P[Int] = Index

  val indexer: StringIndex = defaultIndexer.getOrElse(StringIndex(p.input))

  private def loc(begin: Int, end0: Int): Option[SourcePos] = {
    if (ignoreLocation) return None
    val start = indexer.charIndexToUnicodeLineAndColumn(begin)
    val end = end0 - 1
    val endPos = indexer.charIndexToUnicodeLineAndColumn(end)
    val range = RangeInFile(
      Pos(posOffset + indexer.charIndexToUnicodeIndex(begin), linesOffset + start.line, start.column),
      Pos(posOffset + indexer.charIndexToUnicodeIndex(end), linesOffset + endPos.line, endPos.column))
    Some(SourcePos(fileName, range))
  }

  private def createMeta(pos: Option[SourcePos], comments: Option[CommentInfo]): Option[ExprMeta] = {
    (pos, comments) match {
      case (None, None) => None
      case _ => Some(ExprMeta(pos, comments))
    }
  }

  extension [T](inline parse0: P[T]) {
    inline def withMeta[R](using s: fastparse.Implicits.Sequencer[T, Option[ExprMeta], R]): P[R] = (begin ~ parse0 ~ end).map { case (b, x, e) =>
      val meta = createMeta(loc(b, e), None)
      s(x, meta)
    }

    inline def withSpaceAtStart[R](using s: fastparse.Implicits.Sequencer[T, Vector[Comment], R]): P[R] = (maybeSpace1 ~ parse0).map { case (comments, x) => s(x, comments) }

    inline def must(inline message: String = "Expected something"): P[T] = parse0.? flatMap {
      case Some(x) => Pass(x)
      case None => Fail.opaque(message)./
    }

    inline def on(inline condition: Boolean): P[T] = if condition then parse0 else Fail("")

    inline def checkOn(inline condition: Boolean): P[Unit] = if condition then parse0 else Pass(())

    inline def thenTry(inline parse1: P[T]): P[T] = parse0.?.flatMap {
      case Some(result) => Pass(result)
      case None => parse1
    }
  }

  inline def PwithMeta[T, R](inline parse0: P[T])(using s: fastparse.Implicits.Sequencer[T, Option[ExprMeta], R]): P[R] = P(parse0.withMeta)

  def identifier: P[Identifier] = P(id.withMeta).map { case (name, meta) => Identifier(name, meta) }

  def infixIdentifier: P[Identifier] = P(operatorId.withMeta).map { case (name, meta) => Identifier(name, meta) }

  def signed: P[String] = P("".!) // P(CharIn("+\\-").?.!)

  def hexLiteral: P[String] = P("0x" ~ CharsWhileIn("0-9a-fA-F").must()).!

  def binLiteral: P[String] = P("0b" ~ CharsWhileIn("01").must()).!

  def decLiteral: P[String] = P(CharsWhileIn("0-9")).!

  def expLiteral: P[String] = P(CharsWhileIn("0-9") ~ "." ~ CharsWhileIn("0-9") ~ (CharIn("eE") ~ signed ~ CharsWhileIn("0-9")).?).!

  def integerLiteral: P[ParsedExpr] = P(signed ~ (hexLiteral | binLiteral | decLiteral).!).withMeta.map {
    case (sign, value, meta) =>
      val actualValue = if (value.startsWith("0x")) BigInt(sign + value.drop(2), 16)
      else if (value.startsWith("0b")) BigInt(sign + value.drop(2), 2)
      else BigInt(sign + value)
      IntegerLiteral(actualValue, meta)
  }

  def doubleLiteral: P[ParsedExpr] = P(signed ~ expLiteral.withMeta).map {
    case (sign, (value, meta)) =>
      DoubleLiteral(BigDecimal(sign + value), meta)
  }

  def escapeSequence: P[String] = P("\\" ~ CharIn("rnt\\\"").!).map {
    case "r" => "\r"
    case "n" => "\n"
    case "t" => "\t"
    case "\\" => "\\"
    case "\"" => "\""
  }

  def normalChar: P[String] = P(CharPred(c => c != '\\' && c != '"')).!

  def stringLiteral: P[String] = P("\"" ~ (normalChar | escapeSequence).rep.map(_.mkString) ~ "\"")

  def heredocLiteral: P[String] = {
    def validateIndentation(str: String): Either[String, String] = {
      val lines = str.split("\n")
      val indentStrings = lines.filter(_.trim.nonEmpty).map(_.takeWhile(_.isWhitespace))

      if (indentStrings.distinct.length > 1) Left("Inconsistent indentation in heredoc string literal")
      else {
        val indentSize = if (indentStrings.nonEmpty) indentStrings.head.length else 0
        val trimmedLines = lines.map(_.drop(indentSize))
        Right(trimmedLines.mkString("\n").stripPrefix("\n").stripSuffix("\n"))
      }
    }

    P("\"\"\"" ~ (!"\"\"\"".rep ~ AnyChar).rep.!.flatMap { str =>
      validateIndentation(str) match {
        case Right(validStr) => Pass(validStr)
        case Left(errorMsg) => Fail.opaque(errorMsg)
      }
    } ~ "\"\"\"")
  }

  def stringLiteralExpr: P[ParsedExpr] = P((stringLiteral | heredocLiteral).withMeta).map {
    case (value, meta) => StringLiteral(value, meta)
  }

  def literal: P[ParsedExpr] = P(doubleLiteral | integerLiteral | stringLiteralExpr)

  def simpleAnnotation: P[Identifier] = "@" ~ identifier

  @deprecated
  def comma: P[Unit] = P(maybeSpace ~ "," ~ maybeSpace)

  def comma1: P[Unit] = ","

  def list: P[ListExpr] = PwithMeta("[" ~ (parse().withSpaceAtStart.map((x, c) => x.commentAtStart(c))).rep(sep = comma1) ~ comma.? ~ maybeSpace ~ "]").map { (terms, meta) =>
    ListExpr(terms.toVector, meta)
  }

  def tuple: P[Tuple] = PwithMeta("(" ~ maybeSpace ~ parse().rep(sep = comma) ~ comma.? ~ maybeSpace ~ ")").map { (terms, meta) =>
    Tuple(terms.toVector, meta)
  }

  def annotation: P[(Identifier, Vector[ParsedMaybeTelescope])] = P("@" ~ identifier ~ callingZeroOrMore())

  def annotated: P[AnnotatedExpr] = PwithMeta(annotation ~ parse()).map { case (annotation, telescope, expr, meta) =>
    AnnotatedExpr(annotation, telescope, expr, meta)
  }

  // TODO blockAndLineEndEnds
  case class ParsingContext(inOpSeq: Boolean = false, dontallowOpSeq: Boolean = false, dontallowBiggerSymbol: Boolean = false, dontAllowEqualSymbol: Boolean = false, dontAllowVararg: Boolean = false, newLineAfterBlockMeansEnds: Boolean = false, dontAllowBlockApply: Boolean = false) {
    def opSeq = !inOpSeq && !dontallowOpSeq

    def blockCall = !inOpSeq && !dontAllowBlockApply
  }

  def callingOnce(ctx: ParsingContext = ParsingContext()): P[ParsedMaybeTelescope] = P((list | tuple) | (lineNonEndingSpace.? ~ anonymousBlockLikeFunction.on(ctx.blockCall)).withMeta.map { case (block, meta) =>
    Tuple(Vector(block), meta)
  })

  def callingMultiple(ctx: ParsingContext = ParsingContext()): P[Vector[ParsedMaybeTelescope]] = P(callingOnce(ctx = ctx).rep(min = 1).map(_.toVector))

  def callingZeroOrMore(ctx: ParsingContext = ParsingContext()): P[Vector[ParsedMaybeTelescope]] = P(callingOnce(ctx = ctx).rep.map(_.toVector))

  def functionCall(function: ParsedExpr, p: Option[ExprMeta] => Option[ExprMeta], ctx: ParsingContext = ParsingContext()): P[FunctionCall] = PwithMeta(callingOnce(ctx = ctx)).map { case (telescope, meta) =>
    FunctionCall(function, telescope, p(meta))
  }

  def dotCall(expr: ParsedExpr, p: Option[ExprMeta] => Option[ExprMeta], ctx: ParsingContext = ParsingContext()): P[DotCall] = PwithMeta(maybeSpace ~ "." ~ identifier ~ callingZeroOrMore(ctx = ctx)).map { case (field, telescope, meta) =>
    DotCall(expr, field, telescope, p(meta))
  }

  def insideBlock: P[Block] = PwithMeta((maybeSpace ~ statement).rep ~ maybeSpace ~ parse().? ~ maybeSpace).flatMap { case (heads, tail, meta) =>
    if (heads.isEmpty && tail.isEmpty) Fail("expect something") else Pass(Block(Vector.from(heads), tail, meta))
  }

  def block: P[ParsedExpr] = PwithMeta("{" ~ (maybeSpace ~ statement).rep ~ maybeSpace ~ parse().? ~ maybeSpace ~ "}").flatMap { case (heads, tail, meta) =>
    if (heads.isEmpty && tail.isEmpty) Fail("expect something") else Pass(Block(Vector.from(heads), tail, meta))
  }

  inline def anonymousBlockLikeFunction: P[ParsedExpr] = block | objectParse

  def statement: P[ParsedExpr] = P((parse(ctx = ParsingContext(newLineAfterBlockMeansEnds = true)) ~ Index).flatMap((expr, index) => {
    val itWasBlockEnding = p.input(index - 1) == '}'
    Pass(expr) ~ (maybeSpace ~ ";" | lineEnding.on(itWasBlockEnding))
  }))

  def opSeq(expr: ParsedExpr, p: Option[ExprMeta] => Option[ExprMeta], ctx: ParsingContext): P[OpSeq] = {
    PwithMeta(opSeqGettingExprs(ctx = ctx)).flatMap { case (exprs, meta) =>
      val xs = (expr +: exprs)
      lazy val exprCouldPrefix = expr match {
        case Identifier(name, _) if strIsOperator(name) => true
        case _ => false
      }

      lazy val failEqualCheck = ctx.dontAllowEqualSymbol && xs.exists {
        case Identifier("=", _) => true
        case _ => false
      }

      lazy val failVarargCheck = ctx.dontAllowVararg && xs.exists {
        case Identifier("...", _) => true
        case _ => false
      }

      if (failEqualCheck) {
        Fail("Looks like a equal")
      } else if (failVarargCheck) {
        Fail("Looks like a vararg")
      } else if (!(exprCouldPrefix || xs.exists(_.isInstanceOf[Identifier]))) {
        Fail("Expected identifier")
      } else {
        Pass(OpSeq(xs.toVector, p(meta)))
      }
    }
  }

  def qualifiedNameOn(x: QualifiedName): P[QualifiedName] = PwithMeta("." ~ identifier).flatMap { (id, meta) =>
    val built = QualifiedName.build(x, id, meta)
    qualifiedNameOn(built) | Pass(built)
  }

  def qualifiedName: P[QualifiedName] = P(identifier).flatMap { id =>
    qualifiedNameOn(id) | Pass(id)
  }

  def objectParse: P[ParsedExpr] = PwithMeta("{" ~ (maybeSpace ~ qualifiedName ~ maybeSpace ~ "=" ~ maybeSpace ~ parse() ~ maybeSpace).rep(sep = comma) ~ comma.? ~ maybeSpace ~ "}").map { (fields, meta) =>
    ObjectExpr(fields.toVector, meta)
  }

  def keyword: P[ParsedExpr] = PwithMeta("#" ~ identifier ~ callingZeroOrMore(ParsingContext(dontAllowBlockApply = true))).map { case (id, telescope, meta) =>
    Keyword(id, telescope, meta)
  }

  def opSeqGettingExprs(ctx: ParsingContext): P[Vector[ParsedExpr]] = P(maybeSpace ~ parse(ctx = ctx.copy(inOpSeq = true)) ~ Index).flatMap { (expr, index) =>
    val itWasBlockEnding = p.input(index - 1) == '}'
    ((!lineEnding).checkOn(itWasBlockEnding && ctx.newLineAfterBlockMeansEnds) ~ opSeqGettingExprs(ctx = ctx).map(expr +: _)) | Pass(Vector(expr))
  }

  private def combineMeta(meta1: Option[ExprMeta], meta2: Option[ExprMeta]): Option[ExprMeta] = {
    (meta1, meta2) match {
      case (Some(ExprMeta(pos1, comments1)), Some(ExprMeta(pos2, comments2))) =>
        createMeta(pos1.orElse(pos2), comments1.orElse(comments2))
      case (Some(meta), None) => Some(meta)
      case (None, Some(meta)) => Some(meta)
      case (None, None) => None
    }
  }

  def tailExpr(expr: ParsedExpr, getMeta: Option[ExprMeta] => Option[ExprMeta], ctx: ParsingContext = ParsingContext()): P[ParsedExpr] = P((dotCall(expr, getMeta, ctx) | functionCall(expr, getMeta, ctx = ctx).on(expr.isInstanceOf[Identifier] || expr.isInstanceOf[FunctionCall] || !ctx.inOpSeq) | opSeq(expr, getMeta, ctx = ctx).on(ctx.opSeq)).withMeta ~ Index).flatMap({ (expr, meta, index) => {
    val itWasBlockEnding = p.input(index - 1) == '}'
    val getMeta1 = ((endMeta: Option[ExprMeta]) => getMeta(combineMeta(meta, endMeta)))
    ((!lineEnding).checkOn(itWasBlockEnding && ctx.newLineAfterBlockMeansEnds) ~ tailExpr(expr, getMeta1, ctx = ctx)) | Pass(expr)
  }})

  inline def parse0: P[ParsedExpr] = keyword | objectParse | block | annotated | list | tuple | literal | identifier

  def parse(ctx: ParsingContext = ParsingContext()): P[ParsedExpr] = P(parse0.withMeta ~ Index).flatMap { (expr, meta, index) =>
    val itWasBlockEnding = p.input(index - 1) == '}'
    val getMeta = ((endMeta: Option[ExprMeta]) => combineMeta(meta, endMeta))
    ((!lineEnding).checkOn(itWasBlockEnding && ctx.newLineAfterBlockMeansEnds) ~ tailExpr(expr, getMeta, ctx = ctx)) | Pass(expr)
  }

  def exprEntrance: P[ParsedExpr] = P(Start ~ maybeSpace ~ parse() ~ maybeSpace ~ End)

  def statementsEntrance: P[Vector[ParsedExpr]] = P(Start ~ (maybeSpace ~ statement ~ maybeSpace).rep ~ maybeSpace ~ End).map(_.toVector)

  def toplevelEntrance: P[Block] = P(Start ~ maybeSpace ~ insideBlock ~ maybeSpace ~ End)
}

case class ParseError(message: String, index: Pos)

sealed trait ParserSource

case class FileNameAndContent(fileName: String, content: String) extends ParserSource

case class FilePath(path: String) extends ParserSource

object Parser {
  private def getContentFromSource(source: ParserSource): Either[ParseError, (String, String)] = {
    source match {
      case FileNameAndContent(fileName, content) =>
        Right((fileName, content))
      case FilePath(path) =>
        Try(new String(Files.readAllBytes(Paths.get(path)))) match {
          case Success(content) =>
            val fileName = Paths.get(path).getFileName.toString
            Right((fileName, content))
          case Failure(exception) =>
            Left(ParseError(s"Failed to read file: ${exception.getMessage}", Pos.Zero))
        }
    }
  }

  private def parseFromSource[T](source: ParserSource, parserFunc: ParserInternal => P[T], ignoreLocation: Boolean = false): Either[ParseError, T] = {
    getContentFromSource(source) match {
      case Right((fileName, content)) =>
        val indexer = StringIndex(content)
        parse(content, x => parserFunc(ParserInternal(fileName, ignoreLocation = ignoreLocation, defaultIndexer = Some(indexer))(x))) match {
          case Parsed.Success(result, _) => Right(result)
          case Parsed.Failure(msg, index, extra) =>
            val pos = indexer.charIndexToUnicodeLineAndColumn(index)
            val p = Pos(indexer.charIndexToUnicodeIndex(index), pos.line, pos.column)
            Left(ParseError(s"Parsing failed: ${extra.trace().longMsg}", p))
        }
      case Left(error) => Left(error)
    }
  }

  def parseStatements(source: ParserSource, ignoreLocation: Boolean = false): Either[ParseError, Vector[ParsedExpr]] = {
    parseFromSource(source, _.statementsEntrance, ignoreLocation)
  }

  def parseTopLevel(source: ParserSource, ignoreLocation: Boolean = false): Either[ParseError, Block] = {
    parseFromSource(source, _.toplevelEntrance, ignoreLocation)
  }

  def parseExpr(source: ParserSource, ignoreLocation: Boolean = false): Either[ParseError, ParsedExpr] = {
    parseFromSource(source, _.exprEntrance, ignoreLocation)
  }

  @deprecated("Use parseExpr with ParserSource instead")
  def parseFile(fileName: String): Either[ParseError, ParsedExpr] = {
    parseExpr(FilePath(fileName))
  }

  @deprecated("Use parseExpr with ParserSource instead")
  def parseContent(fileName: String, input: String, ignoreLocation: Boolean = false): Either[ParseError, ParsedExpr] = {
    parseExpr(FileNameAndContent(fileName, input), ignoreLocation)
  }

  @deprecated("Use parseExpr with ParserSource instead")
  def parseExpression(fileName: String, input: String, ignoreLocation: Boolean = false): Parsed[ParsedExpr] = {
    parse(input, x => ParserInternal(fileName, ignoreLocation = ignoreLocation)(x).exprEntrance)
  }

  def extractModuleName(block: Block): Either[ParseError, QualifiedIDString] = {
    block.heads.headOption match {
      case Some(OpSeq(Vector(Identifier("module", _), identifiers*), _)) =>
        val names = identifiers.collect { case Identifier(name, _) => name }.toVector
        if (names.nonEmpty) Right(names) else Left(ParseError("Module identifiers could not be parsed", Pos.Zero))
      case _ => Right(Vector.empty)
    }
  }

  def parseModule(source: ParserSource, modules: Modules = Modules.Empty, ignoreLocation: Boolean = false): Either[ParseError, Modules] = {
    getContentFromSource(source) match {
      case Right((fileName, content)) =>
        parseTopLevel(FileNameAndContent(fileName, content), ignoreLocation).flatMap { block =>
          val moduleFile = ModuleFile(fileName, block)
          extractModuleName(block).map { id =>
            modules.addModule(id, moduleFile)
          }
        }
      case Left(error) => Left(error)
    }
  }
}
