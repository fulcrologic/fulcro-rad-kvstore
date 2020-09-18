cljs:
	shadow-cljs -A:dev server

test:
	clj -A:run-tests:tset -d src/test

base-jar:
	mvn -f pom.xml clean package

redis-jar:
	mvn -f pom_redis.xml clean package
