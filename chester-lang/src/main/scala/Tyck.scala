package chester.lang

import term.*
import ast.*


class TyckMutableState{

}

class Tyck(state: TyckMutableState) {

  def infer(ast: AST): The = ???
  def check(ast: AST, itsType: Term): The = ???
}