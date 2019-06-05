#!/bin/sh

shopping.batch() {
  for i in {1..40}
  do
    http POST localhost:$1/shoppingcart/abc-${i}  productId=$2 quantity:=${i} -v
  done
}
