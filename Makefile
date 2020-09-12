cljs:
	shadow-cljs -A:dev server

test:
	clj -A:run-tests:tset -d src/test
