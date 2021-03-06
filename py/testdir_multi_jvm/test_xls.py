import os, json, unittest, time, shutil, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd

class TestExcel(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=3)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_iris_xls(self):
        h2o_cmd.runRF(None, h2o.find_dataset('iris/iris.xls'), timeoutSecs=5)

    def test_iris_xlsx(self):
        h2o_cmd.runRF(None, h2o.find_dataset('iris/iris.xlsx'), timeoutSecs=5)

    def test_poker_xls(self):
        h2o_cmd.runRF(None, h2o.find_dataset('poker/poker-hand-testing.xls'), timeoutSecs=10)

    def test_poker_xlsx(self):
        h2o_cmd.runRF(None, h2o.find_dataset('poker/poker-hand-testing.xlsx'), timeoutSecs=60)

if __name__ == '__main__':
    h2o.unit_main()
