#!/usr/bin/env python3

# start conllueditor on port 5555
#  cp src/test/resources/test.conllu ..
#  rm -f ../test.conllu.2; ./bin/conlluedit.sh ../test.conllu 5555

# uv run ./ui-test.py 

import time

from selenium import webdriver
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.common.by import By

from selenium.webdriver.firefox.service import Service
#from selenium.webdriver.common.actions import Actions
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.support.select import Select
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC



FF=False
HEADLESS=False

class UITest:
    def __init__(self):
        if FF:
            self.driverService = Service('/snap/bin/geckodriver')
            self.driver = webdriver.Firefox(service=self.driverService)
        else:
            options = webdriver.ChromeOptions()
            options.add_argument("--disable-search-engine-choice-screen")
            if HEADLESS:
                options.add_argument("--headless=new")
            else:
                options.add_argument("--window-size=1420,900")
            self.driver = webdriver.Chrome(options=options)
        self.wait = WebDriverWait(self.driver, 5)
        self.driver.get("http://localhost:5555/")


    def button(self, bid=None, xpath=None):
        
        if xpath:
            print("BUTTON", xpath)
            b = self.driver.find_element(By.XPATH, xpath)
        else:
            print("BUTTON", bid)
            b = self.driver.find_element(By.ID, bid)
        b.click()
        time.sleep(1)
        return b

    def enter_text(self, bid, text, xpath=None):
        if xpath:
            print("TEXT", xpath, text)
            b = self.driver.find_element(By.XPATH, xpath)
        else:
            print("TEXT", bid, text)
            b = self.driver.find_element(By.ID, bid)
        b.clear()
        b.send_keys(text + Keys.RETURN)
        time.sleep(1)

    def enter_key(self, bid, text, xpath=None):
        if xpath:
            print("KEY", xpath, text)
            b = self.driver.find_element(By.XPATH, xpath)
        else:
            print("KEY", bid, text)
            b = self.driver.find_element(By.ID, bid)
        b.send_keys(text)
        time.sleep(1)

    def select(self, bid, choice):
        print("SELECT", bid, choice)
        b = self.driver.find_element(By.ID, bid)
        s = Select(b)
        s.select_by_index(choice)
        time.sleep(1)

    def test_01(self):
        # read sentence
        self.button(bid="lire")

        # get sentence 3
        self.enter_text("sentid", "3")

        # show features
        self.button(bid="feat2")

        # L2R
        self.button(bid="r2l")
        self.button(bid="r2l")

    def test_02(self):
        # open CoNLL-U window
        self.button("conllu")
        print(self.button("rawtext"))

        # close it
        self.button(xpath='/html/body/div[11]/div/div/div[3]/button')

    def test_03(self):
        # show/hide shortcuts
        self.enter_key(bid=None, text="?", xpath="/html/body")
        self.enter_key(bid=None, text="?", xpath="/html/body")

        # hide unhide search
        self.select('searchmode', 3)
        self.select('searchmode', 0)

    def test_04(self):
        # search
        self.enter_text("lemma", text="situer")

    def test_05(self):
        # change head in sentence 5
        # link 11 to new head 6
        w = self.wait.until(EC.element_to_be_clickable((By.XPATH, '//*[@id="rect_11_PUNCT___,_,_punct"]')))
        ac = ActionChains(self.driver)
        ac = ac.move_to_element_with_offset(w, 10, -5)
        ac.click().perform()
        time.sleep(1)

        w = self.wait.until(EC.element_to_be_clickable((By.XPATH, '//*[@id="pos1_6"]')))
        ac = ActionChains(self.driver)
        ac.move_to_element_with_offset(w, 10, -10).click().perform()
        time.sleep(1)

        # edit word 6
        w = self.wait.until(EC.element_to_be_clickable((By.XPATH, '//*[@id="pos1_6"]')))
        ac = ActionChains(self.driver)
        ac.move_to_element_with_offset(w, 10, -10).key_down(Keys.CONTROL).click().key_up(Keys.CONTROL).perform()
        time.sleep(1)
        
        self.enter_text("cform", "LA")
        self.button("checktoken")
        self.button("saveword")

        self.button("next")
        self.button("next")
        self.button("save")

    def test_06(self):
        # edit deprel in sentence 7
        self.button(xpath='//*[@id="textqqpath_3_9_punct"]')
        self.enter_text("cdeprel", "amod")
        self.button("savedeprel")

    def test_07(self):
        # does not work yet
        # edit MWT in sentence 6
        self.enter_text("sentid", "6")

        self.driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
        time.sleep(1)
        w = self.wait.until(EC.element_to_be_clickable((By.XPATH, '//*[@id="mwe_9_10_du"]')))
        ac = ActionChains(self.driver)
        ac.move_to_element_with_offset(w, 0, 0).click().perform()

        self.enter_text('currentMWTmisc', "SpaceAfter=No")
        self.enter_text('currentMWTto', "11")
        self.button("mwtchecktoken")
	
	# save + close modal
        self.button("editMWtoken")

    def test_08(self):
        # choose hedge and table
        self.select("flat3", 1)
        self.select("flat3", 2)

        self.driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
        self.driver.implicitly_wait(1)

        # edit in table
        #input("8: Hit Enter")
        #time.sleep(5) # wait en extra second ... this is not enough...

	# TODO: this crashes from time to time. Why?
        #self.enter_text("tupos_3", Keys.BACKSPACE * 10 + "Titi")
        #self.enter_text("tupos_5", "Titi")
        w = self.wait.until(EC.element_to_be_clickable((By.ID, "tupos_3")))
        ac = ActionChains(self.driver)
        ac.move_to_element_with_offset(w, 0, 0).click().perform()
        w.send_keys("Titi")
        self.button("next")

        # click in table view
        self.button("td5")
        self.enter_key(bid=None, text="D", xpath="/html/body")

        # CTRL-Click in table view
        w = self.button('td5')
        action = ActionChains(self.driver)
        action.key_down(Keys.CONTROL).click(w).key_up(Keys.CONTROL).perform()

        self.enter_key(bid=None, text="D", xpath="/html/body")

        # resize feature column
        self.button('featssizeup')
        self.button('featssizeup')

        self.select("flat3", 0)

    def test_09(self):
        self.button('last')
        self.button('editmetadata')
        self.button('inittranslit')
        self.button('savemetadata')
        self.button('save')
#        input("9: Hit Enter")
        self.button('errormessageclose')
        self.button('adaptwidth')
        self.button('misc2')
        self.button('first')

    def test_10(self):
        self.enter_text('sent_by_ln', "223")
        self.button('lireln')

if __name__ == "__main__":
    ui = UITest()
    ui.test_01()
    ui.test_02()
    ui.test_03()
    ui.test_04()
    ui.test_05()
    ui.test_06()
    ui.test_07()
    ui.test_08()
    ui.test_09()
    ui.test_10()

    # be sure that modified file is saved
    ui.button("save")

    input("hit ENTER finally")

