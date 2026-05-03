package ru.gpn.common

import zio.*

object Time:
  def epochSeconds: UIO[Long] =
    Clock.instant.map(_.getEpochSecond)
