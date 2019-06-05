This repo contains a minimized shopping cart app that I'm using to test new features from Akka 2.6.x.

It contains two almost identical shopping carts app using different ports to avoid port collision. 

Node0 will use:

 * GatewayPort = 9001
 * ServiceLocator = 9002
 * ShoppingCartService = 9003
 * Akka Remote = 2552

Node1 will use:

 * GatewayPort = 10001
 * ServiceLocator = 10002
 * ShoppingCartService = 10003
 * Akka Remote = 2553

 Cluster will be formed using `seed-nodes`. Node0 being the first node.

 File `api.sh` contains some functions to call the service. It uses httpie. 

 If started with `sbt runAll`, it uses Lagom 1.5.1. 
 You can change Lagom's version with `sbt -Dlagom.version=1.6.0-M2 runAll`