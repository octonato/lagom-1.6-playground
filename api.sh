#!/bin/sh

shopping.batch() {
  for i in {1..40}
  do
    http POST localhost:$3/shoppingcart/abc-${i}  productId=$1 quantity:=$2 -v
  done
}
