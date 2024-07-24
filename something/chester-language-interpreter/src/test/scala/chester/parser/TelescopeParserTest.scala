package chester.parser
import munit.FunSuite
import fastparse.*
import chester.syntax.concrete._
import chester.parser._

class TelescopeParserTest extends FunSuite {

  def parseAndCheck(input: String, expected: Expr): Unit = {
    val result = Parser.parseExpression("testFile", input, ignoreLocation = true)
    result match {
      case Parsed.Success(value, _) =>
        assertEquals(value, expected, s"Failed for input: $input")
      case Parsed.Failure(label, index, extra) => fail(s"Parsing failed for input: $input $label $index ${extra.trace().msg}")
    }
  }

  test("parse telescope with simple arguments") {
    val input = "(a, b, c)"
    val expected = Telescope(Vector(
      Arg(Vector.empty, None, None, Some(Identifier("a"))),
      Arg(Vector.empty, None, None, Some(Identifier("b"))),
      Arg(Vector.empty, None, None, Some(Identifier("c")))
    ))
    parseAndCheck(input, expected)
  }

  test("parse telescope with arguments having types") {
    val input = "(a: Integer, b: String, c: Double)"
    val expected = Telescope(Vector(
      Arg(Vector.empty, Some(Identifier("a")), Some(Identifier("Integer")), None),
      Arg(Vector.empty, Some(Identifier("b")), Some(Identifier("String")), None),
      Arg(Vector.empty, Some(Identifier("c")), Some(Identifier("Double")), None)
    ))
    parseAndCheck(input, expected)
  }

  test("parse telescope with arguments having default values") {
    val input = "(a = 1, b = 2, c = 3)"
    val expected = Telescope(Vector(
      Arg(Vector.empty, Some(Identifier("a")), None, Some(IntegerLiteral(1))),
      Arg(Vector.empty, Some(Identifier("b")), None, Some(IntegerLiteral(2))),
      Arg(Vector.empty, Some(Identifier("c")), None, Some(IntegerLiteral(3)))
    ))
    parseAndCheck(input, expected)
  }

  test("parse telescope with arguments having types and default values") {
    val input = "(a: Integer = 1, b: String = \"test\", c: Double = 3.14)"
    val expected = Telescope(Vector(
      Arg(Vector.empty, Some(Identifier("a")), Some(Identifier("Integer")), Some(IntegerLiteral(1))),
      Arg(Vector.empty, Some(Identifier("b")), Some(Identifier("String")), Some(StringLiteral("test"))),
      Arg(Vector.empty, Some(Identifier("c")), Some(Identifier("Double")), Some(DoubleLiteral(BigDecimal(3.14))))
    ))
    parseAndCheck(input, expected)
  }

  test("parse telescope with arguments having decorations") {
    val input = "(#implicit a: Integer = 1, b: Integer, c)"
    val expected = Telescope(Vector(
      Arg(Vector(Identifier("implicit")), Some(Identifier("a")), Some(Identifier("Integer")), Some(IntegerLiteral(1))),
      Arg(Vector.empty, Some(Identifier("b")), Some(Identifier("Integer")), None),
      Arg(Vector.empty, None, None, Some(Identifier("c")))
    ))
    parseAndCheck(input, expected)
  }

  test("parse telescope with mixed arguments") {
    val input = "(a, b: String, c = 3.14, #implicit d: Integer = 1)"
    val expected = Telescope(Vector(
      Arg(Vector.empty, None, None, Some(Identifier("a"))),
      Arg(Vector.empty, Some(Identifier("b")), Some(Identifier("String")), None),
      Arg(Vector.empty, Some(Identifier("c")), None, Some(DoubleLiteral(BigDecimal(3.14)))),
      Arg(Vector(Identifier("implicit")), Some(Identifier("d")), Some(Identifier("Integer")), Some(IntegerLiteral(1)))
    ))
    parseAndCheck(input, expected)
  }

  test("parse telescope with arguments without names") {
    val input = "(1, 2, 3)"
    val expected = Telescope(Vector(
      Arg(Vector.empty, None, None, Some(IntegerLiteral(1))),
      Arg(Vector.empty, None, None, Some(IntegerLiteral(2))),
      Arg(Vector.empty, None, None, Some(IntegerLiteral(3)))
    ))
    parseAndCheck(input, expected)
  }
}