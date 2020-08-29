cljs:
	shadow-cljs -A:dev server

test:
	clj -A:run-tests:test -d src/test
