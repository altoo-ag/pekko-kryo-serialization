package io.altoo.external

enum ExternalEnum(val name: String) {
  case A extends ExternalEnum("a")
}