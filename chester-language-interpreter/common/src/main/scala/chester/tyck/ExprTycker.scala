package chester.tyck

import chester.syntax.concrete.*
import chester.syntax.core._

case class TyckState()

case class LocalCtx()

case class Judge(wellTyped: Term, ty: Term)

case class TyckError(message: String)

object TyckError {
  val emptyResults = TyckError("Empty Results")
}

case class Getting[T](xs: TyckState => LazyList[Either[TyckError, (TyckState, T)]]) {

  def map[U](f: T => U): Getting[U] = Getting { state =>
    xs(state).map {
      case Left(err) => Left(err)
      case Right((nextState, value)) => Right((nextState, f(value)))
    }
  }

  def flatMap[U](f: T => Getting[U]): Getting[U] = Getting { state =>
    xs(state).flatMap {
      case Left(err) => LazyList(Left(err))
      case Right((nextState, value)) => f(value).xs(nextState)
    }
  }

  def getOne(state: TyckState): Either[TyckError, (TyckState, T)] = {
    xs(state).collectFirst {
      case right@Right(_) => right
    }.getOrElse(Left(TyckError.emptyResults))
  }

  def explainError(explain: TyckError => TyckError): Getting[T] = Getting { state =>
    xs(state).map {
      case Left(err) => Left(explain(err))
      case right => right
    }
  }
}

object Getting {
  def pure[T](x: T): Getting[T] = Getting(state => LazyList(Right((state, x))))

  def error[T](err: TyckError): Getting[T] = Getting(_ => LazyList(Left(err)))

  def read: Getting[TyckState] = Getting(state => LazyList(Right((state, state))))

  def write(newState: TyckState): Getting[Unit] = Getting(_ => LazyList(Right((newState, ()))))
}


case class ExprTycker(localCtx: LocalCtx) {
  def unify(subType: Term, superType: Term): Getting[Term] = {
    if (subType == superType) return Getting.pure(subType)
    ???
  }

  def inherit(expr: Expr, ty: Term): Getting[Judge] = expr match {
    case default => for {
      Judge(wellTyped, judgeTy) <- synthesize(default)
      ty1 <- unify(judgeTy, ty)
    } yield Judge(wellTyped, ty1)
  }

  def synthesize(expr: Expr): Getting[Judge] = expr match {
    case IntegerLiteral(value, sourcePos, _) => Getting.pure(Judge(IntegerTerm(value, sourcePos), IntegerType()))
    case _ => ???
  }
}
