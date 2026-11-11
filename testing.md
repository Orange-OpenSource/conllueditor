# Tests

## Server

The back-end server of ConlluEditor come with a set of unitary tests which are run automatically after compilation:
* `mvn install`
  * if the tests fail after `git pull` try deleting `target/test-classes` and `testoutput`. If this does not solve the problem, run `mvn install -DskipTests` to avoid running the JUnit tests and drop me a note

* individual tests can be run by `mvn test -Dtest=TestConlluEditor#<testmethod>` (check test names in `src/test/java/Test*.java`)
* redirect stderr in order to see only maven messages (test reports are generated in `target/surefire-reports/`):
  * `mvn install 2> stderr_output.txt`
  * `mvn test -Dtest=TestConlluEditor#<testmethod>  2> stderr_output.txt`

## User Interface

The front end is tests using Chrome/Chromium, python, [uv](https://docs.astral.sh/uv/) and the [selenium](https://pypi.org/project/selenium/) package:
  * have Chrome/Chromium and uv installed
  * `uv sync`
  * copy `test.conllu` to non-git directory `cp src/test/resources/test.conllu /tmp`
  * make sure no old backup file exists and start server `\rm /tmp/test.conllu.2 ; ./bin/conlluedit.sh  /tmp/test.conllu 5556`
  * run UI test: `uv run bin/uitest.py`. The tests opens a Chrome browser. Sometimes it helps to make it larger, but do not touch it otherwise during the tests 😀
  * since the UI tests modify the file loaded by the server (and not all changes are undone by the test) you have to restart the server before each run of `uitest.py`
    * for other options run `uv run bin/uitest.py -h`
    * NOTE: tests with on firefox (`--browser firefox`) fail currently, as do headless tests (`--headless`)

## Docker

The docker container can be tested in the same way as the UI:
have Chrome/Chromium and uv installed
  * `uv sync`
  * copy `test.conllu` to non-git directory `cp src/test/resources/test.conllu /tmp`
  * make sure no old backup file exists and start server
    * `\rm /tmp/test.conllu.2`
    * `docker run --rm -t  --name ce -p 5556:5555 -v /tmp:/data --env filename=test.conllu jheinecke/conllueditor:latest`
  * run UI test: `uv run bin/uitest.py`.
