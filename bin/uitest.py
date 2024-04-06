#!/usr/bin/env python3

# tests the GUI using selenium
# pip install selenium
# start ConlluEditor with following (test file must not be git controlled)
#   cp sr/test/resources/test.conllu /tmp
#   ./bin/conlluedit.sh /tmp/test.conllu 5556

import json
import re
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


class CE_UItest:
    def __init__(self, args):

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

        for test in tests:
            print("Test", test["name"])
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
                else:
                    print("\tKO: Bad test function", test["function"][0])
                if hyp:
                    if hyp == test["expected"]:
                        print("\tOK")
                    else:
                        print("\tKO")
                        print("\texpected", test["expected"])
                        print("\tgot     ", hyp, json.dumps(hyp))
            except Exception as e:
                print("\tKO", e)


    def __del__(self):
        input(">>")
        self.driver.close()


    def getconllu(self):
        print("** get CoNLL-U window")
        w = self.driver.find_element(By.ID, "conllu")
        w.click()
        # get text of Conll-U window
        w2 = self.driver.find_element(By.ID, "rawtext")
        text = w2.get_attribute('innerHTML')
        time.sleep(1)
        w5 = self.driver.find_element(By.XPATH, '/html/body/div[11]/div/div/div[3]/button') # close
        w5.click()

        # run undo after test
        print("** undo")
        w6 = self.driver.find_element(By.ID, "undo")
        w6.click()
        time.sleep(0.5)
        
        return text

    def getpage(self, pn=5):
        print("** read sentence %s" % pn)
        sentid = self.driver.find_element(By.ID, "sentid")
        sentid.clear()
        sentid.send_keys(str(pn) + Keys.RETURN)
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

        w2 = self.driver.find_element(By.ID, 'currentMWTform')
        w2.send_keys("desbat"+ Keys.RETURN)
        w3 = self.driver.find_element(By.ID, 'currentMWTto')
        w3.clear()
        w3.send_keys("10"+ Keys.RETURN)
        w4 = self.driver.find_element(By.ID, 'editMWtoken') # save
        w4.click()
        time.sleep(1)
        return self.getconllu()

    def changeDeprel(self, pn=5, elem='//*[@id="textpath_7_6_det"]'):
        self.getpage(pn)
        w = self.driver.find_element(By.XPATH, elem)
        w.click()
        w2 = self.driver.find_element(By.ID, 'cdeprel')
        w2.clear()
        w2.send_keys("amod"+ Keys.RETURN)
        w3 = self.driver.find_element(By.ID, 'savedeprel')
        w3.click()
        return self.getconllu()

    def search(self, lemma):
        b = self.driver.find_element(By.ID, "lemma")
        b.clear()
        b.send_keys(lemma + Keys.RETURN)
        return self.getconllu()

    def tablemode(self, pn=9):
        self.getpage(pn)
        w = self.driver.find_element(By.ID, 'next')
        w.click()

        # choose tree/hedge/table
        w = self.driver.find_element(By.ID, 'flat3')
        s = Select(w)
        s.select_by_index(2) # table


        # Click
        w = self.driver.find_element(By.ID, 'td3')
        action = ActionChains(self.driver)
        action.click(w).perform()
        b = self.driver.find_element(By.XPATH, '/html/body')
        b.send_keys("D")
        time.sleep(1)

        # CTRL-Click in table view
        w = self.driver.find_element(By.ID, 'td5')
        #action = ActionChains(driver)
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
        return self.getconllu()


    def edittable(self, pn=2, edits=[]):
        self.getpage(pn)
        w = self.driver.find_element(By.ID, 'flat3')
        s = Select(w)
        s.select_by_index(2) # table
        time.sleep(0.5)
        #print("ZZZZ", edits)
        for edit in edits:
            #if ix < 1:
            #    continue
            #print("zzzz", edit)
            w1 = self.driver.find_element(By.ID, edit[0])
            #w1 = self.driver.find_element(By.XPATH, edit[0])
            #w1.clear()
            w1.send_keys(Keys.BACKSPACE*10 + edit[1] + Keys.RETURN)
            time.sleep(1)
            w1 = self.driver.find_element(By.ID, "tlemma_1")
            w1.click()
            time.sleep(1)
            #input("QQQ ")
        return self.getconllu()

#w = driver.find_element(By.ID, 'tupos_3')
#w.clear()
#w.send_keys("TOTO"+ Keys.RETURN)


#w = driver.find_element(By.ID, 'next')
#w.click()



if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()

    parser.add_argument("--browser", "-b", default="firefox", type=str, help="browser to use: firefox/chrome")
    parser.add_argument("--headless", "-H", default=False, action="store_true", help="do not show browser window")
    parser.add_argument("--gecko", "-g", default="/snap/bin/geckodriver", type=str, help="path to geckodreiver (needed with firefox)")
    parser.add_argument("--url", "-u", default="http://localhost:5556", type=str, help="URL of ConlluEditor")
    parser.add_argument("--filter", "-f", default=None, type=str, help="regex to filter tests")


    if len(sys.argv) < 2:
        parser.print_help()

    args = parser.parse_args()

    uit = CE_UItest(args)
    uit.runtests()
        
