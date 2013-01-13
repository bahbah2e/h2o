import unittest
import random, sys, time, os
sys.path.extend(['.','..','py'])

# FIX! add cases with shuffled data!
import h2o, h2o_cmd, h2o_hosts, h2o_glm
import h2o_browse as h2b, h2o_import as h2i, h2o_exec as h2e

def write_syn_dataset(csvPathname, rowCount, colCount, SEED, translateList):
    # do we need more than one random generator?
    r1 = random.Random(SEED)
    r2 = random.Random(SEED)
    dsf = open(csvPathname, "w+")

    for i in range(rowCount):
        rowData = []
        for j in range(colCount):
            ri1 = int(r1.triangular(0,4,2.5))
            rowData.append(ri1)

        rowTotal = sum(rowData)
        if translateList is not None:
            for i, iNum in enumerate(rowData):
                rowData[i] = translateList[iNum]

        result = r2.randint(0,1)
        ### print colCount, rowTotal, result
        rowDataStr = map(str,rowData)
        rowDataStr.append(str(result))
        rowDataCsv = ",".join(rowDataStr)
        dsf.write(rowDataCsv + "\n")

    dsf.close()


paramDict = {
    'family': ['binomial'],
    # 'norm': ['ELASTIC'],
    'norm': ['L2'],
    'lambda_1': [1.0E-5],
    'lambda_2': [1.0E-8],
    'alpha': [1.0],
    'rho': [0.01],
    'max_iter': [50],
    'weight': [1.0],
    'threshold': [0.5],
    # 'case': [NaN],
    # 'case': [None],
    # 'link': [familyDefault],
    'xval': [2],
    'expand_cat': [0],
    'beta_eps': [1.0E-4],
    }

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = random.randint(0, sys.maxint)
        # SEED = 
        random.seed(SEED)
        print "\nUsing random seed:", SEED
        global local_host
        local_host = not 'hosts' in os.getcwd()
        if (local_host):
            h2o.build_cloud(2,java_heap_GB=7,use_flatfile=True)
        else:
            h2o_hosts.build_cloud_with_hosts()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_GLM_many_cols_4(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        translateList = ['a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u']
        tryList = [
            (1000000,  100, 'cA', 600),
            ]

        ### h2b.browseTheCloud()

        for (rowCount, colCount, key2, timeoutSecs) in tryList:
            SEEDPERFILE = random.randint(0, sys.maxint)
            csvFilename = 'syn_' + str(SEEDPERFILE) + "_" + str(rowCount) + 'x' + str(colCount) + '.csv'
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            print "\nCreating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE, translateList)

            print "\nUpload and parse", csvPathname
            parseKey = h2o_cmd.parseFile(None, csvPathname, key2=key2, timeoutSecs=240, retryDelaySecs=0.5)
            print csvFilename, 'parse time:', parseKey['response']['time']
            print "Parse result['destination_key']:", parseKey['destination_key']

            # We should be able to see the parse result?
            inspect = h2o_cmd.runInspect(None, parseKey['destination_key'])
            print "\n" + csvFilename

            paramDict2 = {}
            for k in paramDict:
                paramDict2[k] = paramDict[k][0]

            # since we add the output twice, it's no longer colCount-1
            y = colCount
            kwargs = {'y': y, 'max_iter': 50, 'case': 'NaN'}
            kwargs.update(paramDict2)

            start = time.time()
            glm = h2o_cmd.runGLMOnly(parseKey=parseKey, timeoutSecs=timeoutSecs, **kwargs)
            print "glm end on ", csvPathname, 'took', time.time() - start, 'seconds'
            # only col Y-1 (next to last)doesn't get renamed in coefficients due to enum/categorical expansion
            print "y:", y 
            # FIX! bug was dropped coefficients if constant column is dropped
            ### h2o_glm.simpleCheckGLM(self, glm, Y-2, **kwargs)
            h2o_glm.simpleCheckGLM(self, glm, None, **kwargs)

if __name__ == '__main__':
    h2o.unit_main()
