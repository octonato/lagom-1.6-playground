#!/bin/sh

shopping.update.batch() {
  for i in {1..40}
  do
    http POST localhost:$1/shoppingcart/$2-${i}  productId=$2 quantity:=${i} -v
  done
}

shopping.report() {
  http POST localhost:$1/shoppingcart/$2/checkout
}

shopping.get() {
  http localhost:$1/shoppingcart/$2
}

shopping.get.batch() {
  for i in {1..40}
  do
    http localhost:$1/shoppingcart/$2-${i}
  done
}

shopping.report() {
  http localhost:$1/shoppingcart/$2/report
}
