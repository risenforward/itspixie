package com.prisma.api.database.mutactions

case class MutactionGroup(mutactions: List[Mutaction], async: Boolean) {

  // just for debugging!
  def unpackTransactions: List[Mutaction] = {
    mutactions.flatMap {
      case t: TransactionMutaction => t.clientSqlMutactions
      case x              => Seq(x)
    }
  }
}
