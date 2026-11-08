#!/usr/bin/env python3

# This library is under the 3-Clause BSD License

# Copyright (c) 2018-2026, Orange S.A.

# Redistribution and use in source and binary forms, with or without modification,
# are permitted provided that the following conditions are met:
#
#   1. Redistributions of source code must retain the above copyright notice,
#      this list of conditions and the following disclaimer.

#   2. Redistributions in binary form must reproduce the above copyright notice,
#      this list of conditions and the following disclaimer in the documentation
#      and/or other materials provided with the distribution.

#   3. Neither the name of the copyright holder nor the names of its contributors
#      may be used to endorse or promote products derived from this software without
#      specific prior written permission.

# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
# THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
# (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
# ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
#  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
#  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

#  @author Johannes Heinecke
#  @version 3.0.0 as of 2nd November 2026

# tests the GUI using selenium
# pip install selenium
# start ConlluEditor with following (test file must not be git controlled)
#   cp sr/test/resources/test.conllu /tmp
#   ./bin/conlluedit.sh /tmp/test.conllu 5556

import difflib
import json
import re
import readline
import sys
import time


from selenium import webdriver
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.common.by import By

from selenium.webdriver.firefox.service import Service
from selenium.webdriver.firefox.options import Options
#from selenium.webdriver.common.actions import Actions
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.support.select import Select
from selenium.common.exceptions import *

# TODO:
# put all test steps/data into uitest.json

class CE_UItest:
    def __init__(self, args):
        #print("args", args)
        if args.browser == "firefox":
            options = webdriver.FirefoxOptions()
            if args.headless:
                # creates different results
                options.add_argument('--headless')
                options.add_argument('--window-size=1920,1380')
                # options.headless = args.headless

            self.driverService = Service(args.gecko)
            self.driver = webdriver.Firefox(service=self.driverService, options=options)
        else:
            options = webdriver.ChromeOptions()
            if args.headless:
                options.add_argument('--headless')

            self.driver = webdriver.Chrome(options=options)
        self.driver.get(args.url)
        self.args = args


    def runtests(self, testjson="bin/uitest.json"):
        tests = json.load(open(testjson))
        #print(json.dumps(tests, ensure_ascii=False, indent=2))

        tfilter = None
        if self.args.filter:
            tfilter = re.compile(self.args.filter)

        for num, test in enumerate(tests, 1):
            print(f"{num} Test {test['name']}")
            if "ignore" in test and test["ignore"]:
                print("\tignored")
                continue
            if tfilter and not tfilter.search(test["name"]):
                print("\tfiltered")
                continue

            hyp = None
            try:
                if test["function"][0] == "readpage":
                    hyp = self.readpage(test["function"][1])
                elif test["function"][0] == "editMWT":
                    hyp = self.editMWT(test["function"][1], test["function"][2])
                elif test["function"][0] == "changeDeprel":
                    hyp = self.changeDeprel(test["function"][1], test["function"][2])
                elif test["function"][0] == "search":
                    hyp = self.search(test["function"][1])
                elif test["function"][0] == "tablemode":
                    hyp = self.tablemode(test["function"][1])
                elif test["function"][0] == "edittable":
                    hyp = self.edittable(test["function"][1], test["function"][2])
                elif test["function"][0] == "changehead":
                    hyp = self.changeHead(test["function"][1], test["function"][2], test["function"][3])
                elif test["function"][0] == "modifyword":
                    hyp = self.modifyWord(test["function"][1], test["function"][2])
                elif test["function"][0] == "shortcuts":
                    hyp = self.shortcuts(test["function"][1], test["function"][2])
                elif test["function"][0] == "changeEnhancedDeprel":
                    hyp = self.changeEnhancedDeprel(test["function"][1], test["function"][2])
                elif test["function"][0] == "changeEnhancedHead":
                    hyp = self.changeEnhancedHead(test["function"][1], test["function"][2], test["function"][3])

                else:
                    print("\tKO: Bad test function", test["function"][0])
                if hyp:
                    if hyp == test["expected"]:
                        print("\tOK")
                    else:
                        print("\tKO")
                        #print("\texpected", test["expected"])
                        #print("\tgot     ", hyp)
                        #print(json.dumps(hyp, ensure_ascii=False))

                        for x in difflib.context_diff(test["expected"].split("\n"),
                                                      hyp.split("\n"),
                                                      fromfile="expected", tofile="got"):
                            print(x)
                        input("KO>>")
            except WebDriverException as e:
                print("\tKO", type(e), e)
                input("EXCEPTION>>")

    def __del__(self):
        input("Hit ENTER to close >>")
        #self.driver.close()


    def getconllu(self, undos=1):
        # gets results (via CoNLL-U window) and undoes test
        if self.args.pause:
            input("hit ENTER to undo and continue >>")
        print("** get CoNLL-U window")

        self.clickbutton("conllu")
        # get text of Conll-U window
        w2 = self.driver.find_element(By.ID, "rawtext")
        text = w2.get_attribute('innerHTML')

        time.sleep(1)
        w5 = self.driver.find_element(By.XPATH, '/html/body/div[11]/div/div/div[3]/button') # close
        w5.click()

        time.sleep(0.5)
        # run undo after test
        print("** undo")
        #w6 = self.driver.find_element(By.ID, "undo")
        #w6.click()
        for x in range(undos):
            self.clickbutton("undo")
            time.sleep(0.5)

        return text

    def getpage(self, pn=5):
        print("** read sentence %s" % pn)
        #sentid = self.driver.find_element(By.ID, "sentid")
        #sentid.clear()
        #sentid.send_keys(str(pn) + Keys.RETURN)
        self.entertext("sentid", str(pn) + Keys.RETURN, True)
        time.sleep(0.5)

    def readpage(self, pn=5):
        self.getpage(pn)
        return self.getconllu()
        #time.sleep(1)

    def editMWT(self, pn=5, mwt='#mwe_8_9_des'):
        self.getpage(pn)
        w = self.driver.find_element(By.CSS_SELECTOR, mwt)
        #w.click() # FF
        ac=ActionChains(self.driver)
        ac.move_to_element(w).move_by_offset(0,0).click().perform() ## chrome

        self.entertext("currentMWTform", "desbat")

        self.entertext("currentMWTto", "10", True)

        self.clickbutton('editMWtoken') # save

        time.sleep(1)
        return self.getconllu()

    def changeDeprel(self, pn=5, elem='//*[@id="textpath_7_6_det"]'):
        self.getpage(pn)
        w = self.driver.find_element(By.XPATH, elem)
        w.click()

        self.entertext("cdeprel", "amod", True)

        self.clickbutton('savedeprel')
        return self.getconllu(2)

    def changeEnhancedDeprel(self, pn=5, elem=None):
        self.getpage(pn)
        self.choose("flat3", 1)
        self.clickbutton("edit_ed_container")

        w = self.driver.find_element(By.XPATH, elem)
        w.click()

        self.entertext("cdeprelen", "amodnsubj", True)

        self.clickbutton('savedeprelen')
        self.clickbutton("edit_ed_container")
        res = self.getconllu()

        self.choose("flat3", 0)
        return res

    def modifyWord(self, pn=6, elem=None):
        self.getpage(pn)
        wf = self.driver.find_element(By.CSS_SELECTOR, elem)
        # we need to click on the background of the wordnode, not on the words
        ac=ActionChains(self.driver)
        ac.move_to_element(wf).move_by_offset(5,5).key_down(Keys.CONTROL).click().key_up(Keys.CONTROL).perform() ## chrome

        self.entertext("clemma", "Pöùř", True)
        self.entertext("cform", "für", True)
        self.entertext("cupos", "PREP", True)

        self.clickbutton('checktoken')
        self.clickbutton('saveword')

        return self.getconllu(5)

    def search(self, lemma):
        self.getpage(1)
        self.entertext("lemma", lemma + Keys.RETURN, True)
        return self.getconllu()

    def changeHead(self, pn=6, elem1=None, elem2=None):
        self.getpage(pn)
        wf = self.driver.find_element(By.CSS_SELECTOR, elem1)
        # we need to click on the background of the wordnode, not on the words
        ac = ActionChains(self.driver)
        ac.move_to_element(wf).move_by_offset(5,5).click().perform() ## chrome

        wt = self.driver.find_element(By.CSS_SELECTOR, elem2)
        ac = ActionChains(self.driver)
        ac.move_to_element(wt).move_by_offset(5,5).click().perform()

        return self.getconllu()

    def changeEnhancedHead(self, pn=13, elem1=None, elem2=None):
        self.getpage(pn)

        self.choose("flat3", 1)
        self.clickbutton("edit_ed_container")
        wf = self.driver.find_element(By.CSS_SELECTOR, elem1)
        # we need to click on the background of the wordnode, not on the words
        ac = ActionChains(self.driver)
        ac.move_to_element(wf).move_by_offset(5,5).click().perform()

        wt = self.driver.find_element(By.CSS_SELECTOR, elem2)
        ac = ActionChains(self.driver)
        ac.move_to_element(wt).move_by_offset(5,5).click().perform()

        self.entertext("cdeprelen", "neu")
        self.clickbutton("savedeprelen")

        self.choose("flat3", 0)
        return self.getconllu()


    def shortcuts(self, pn=18, elem=None):
        self.getpage(pn)
        self.clickbutton("featb")
        self.clickbutton("miscb")
        wf = self.driver.find_element(By.CSS_SELECTOR, elem)
        # we need to click on the background of the wordnode, not on the words
        ac = ActionChains(self.driver)
        ac.move_to_element(wf).move_by_offset(20,0).click().perform()
        time.sleep(1)

        b = self.driver.find_element(By.XPATH, '/html/body')
        b.send_keys(":p1")
        time.sleep(.5)

        wf = self.driver.find_element(By.CSS_SELECTOR, elem)
        ac.move_to_element(wf).move_by_offset(20,0).click().perform()
        b.send_keys(":san")
        time.sleep(.5)

        wf = self.driver.find_element(By.CSS_SELECTOR, elem)
        ac.move_to_element(wf).move_by_offset(20,0).click().perform()
        b.send_keys("AV")
        time.sleep(.5)

        return self.getconllu(3)

    def tablemode(self, pn=9):
        self.getpage(pn)
        self.clickbutton("next")

        # choose tree/hedge/table
        self.choose("flat3", 2) # table

        # Click
        w = self.driver.find_element(By.ID, 'td3')
        action = ActionChains(self.driver)
        action.click(w).perform()
        b = self.driver.find_element(By.XPATH, '/html/body')
        b.send_keys("D")
        time.sleep(1)

        # CTRL-Click in table view
        w = self.driver.find_element(By.ID, 'td5')

        action.key_down(Keys.CONTROL).click(w).key_up(Keys.CONTROL).perform()
        b = self.driver.find_element(By.XPATH, '/html/body')
        b.send_keys("V")
        time.sleep(1)
        b.send_keys("_")
        time.sleep(1)
        b.send_keys(":p2")
        time.sleep(1)
        w = self.driver.find_element(By.ID, 'td5')
        action.click(w).perform()
        time.sleep(1)

        res = self.getconllu(4)

        # choose tree/hedge/table
        self.choose("flat3", 0) # tree
        return res

    def edittable(self, pn=2, edits=[]):
        self.getpage(pn)
        self.choose("flat3", 2) # tree

        time.sleep(0.5)
        #print("ZZZZ", edits)
        for edit in edits:

            w1 = self.driver.find_element(By.ID, edit[0])
            w1.send_keys(Keys.BACKSPACE*10 + edit[1] + Keys.RETURN)
            time.sleep(1)
            w1 = self.driver.find_element(By.ID, "tlemma_1")
            w1.click()
            time.sleep(0.5)

        # save the current version before going back to tree-view (since this removes the undo states)
        res = self.getconllu(3)

        # choose tree/hedge/table
        self.choose("flat3", 0)

        return res #self.getconllu()

    def clickbutton(self, ident):
        w = self.driver.find_element(By.ID, ident)
        w.click()

    def entertext(self, name, text, clear=False):
        we = self.driver.find_element(By.ID, name)
        time.sleep(0.5)
        if clear:
            we.clear()

        we.send_keys(text)

    def choose(self, name, number):
        w = self.driver.find_element(By.ID, name)
        s = Select(w)
        s.select_by_index(number)



if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser("ConlluEditor UItest",
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument("--browser", "-b", default="chrome", type=str, help="browser to use: firefox/chrome")
    parser.add_argument("--headless", "-H", default=False, action="store_true", help="do not show browser window")
    parser.add_argument("--pause", "-p", default=False, action="store_true", help="wait for ENTER after each test")
    parser.add_argument("--gecko", "-g", default="/snap/bin/geckodriver", type=str, help="path to geckodreiver (needed with firefox)")
    parser.add_argument("--url", "-u", default="http://localhost:5556", type=str, help="URL of ConlluEditor")
    parser.add_argument("--filter", "-f", default=None, type=str, help="regex to filter tests")

    if len(sys.argv) < 2:
        parser.print_help()

    args = parser.parse_args()

    uit = CE_UItest(args)
    uit.runtests()

