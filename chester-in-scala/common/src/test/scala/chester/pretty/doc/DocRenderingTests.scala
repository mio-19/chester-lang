package chester.pretty.doc

import munit.FunSuite

import Doc._

class DocRenderingTests extends FunSuite {

  test("Render single text") {
    val doc = text("Hello, World!")
    val rendered = render(doc, 80)(StringRenderer)
    assertEquals(rendered, "Hello, World!")
  }

  test("Render concatenated text") {
    val doc = text("Hello") <+> text("World")
    val rendered = render(doc, 80)(StringRenderer)
    assertEquals(rendered, "Hello World")
  }

  test("Render new line text") {
    val doc = text("Hello") </> text("World")
    val rendered = render(doc, 80)(StringRenderer)
    assertEquals(rendered, "Hello World")
  }

  test("Render grouped text") {
    val doc = group(text("Grouped Text"))
    val rendered = render(doc, 80)(StringRenderer)
    assertEquals(rendered, "Grouped Text")
  }

  test("Render empty text") {
    val doc = text("")
    val rendered = render(doc, 80)(StringRenderer)
    assertEquals(rendered, "")
  }

  test("Render indented text with spaces") {
    val doc = indented(Indent.Spaces(4), text("Indented Text"))
    val rendered = render(doc, 80)(StringRenderer)
    assertEquals(rendered, "    Indented Text")
  }

  test("Render indented text with tabs") {
    val doc = indented(Indent.Tab, text("Indented Text"))
    val rendered = render(doc, 80)(StringRenderer)
    assertEquals(rendered, "\tIndented Text")
  }

  test("Render mixed document") {
    val doc = text("Hello") <+> text("World") </> indented(Indent.Spaces(2), text("Indented Text"))
    val rendered = render(doc, 999)(StringRenderer)
    assertEquals(rendered, "Hello World Indented Text")
  }
  test("Render mixed document") {
    val doc = text("Hello") <+> text("World") </> indented(Indent.Spaces(2), text("Indented Text"))
    val rendered = render(doc, 2)(StringRenderer)
    assertEquals(rendered, "Hello World\n  Indented Text")
  }
}
