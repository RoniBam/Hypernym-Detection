package jobs;

import DataTypes.CounterType;
import DataTypes.DependencyPath;
import DataTypes.NounPair;
import DataTypes.SyntacticNgram;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;

import javax.script.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ParseCorpus {


    public static class MapperClass extends Mapper<LongWritable, Text, DependencyPath, NounPair> {
        public static String runpythonScript(String[] args) throws Exception {
            StringWriter writer = new StringWriter();
            ScriptContext context = new SimpleScriptContext();

            context.setWriter(writer);

            ScriptEngineManager manager = new ScriptEngineManager();
            System.out.println(manager.getEngineFactories().toString());
            ScriptEngine engine = manager.getEngineByName("python");
            Bindings bindings = engine.createBindings();
            bindings.put("args", args);
            context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
            engine.eval(new FileReader(resolvePythonScriptPath("hello.py")), context);
            return writer.toString().trim();
        }

        private static String resolvePythonScriptPath(String path) {
            File file = new File(path);
            return file.getAbsolutePath();
        }

        @Override
        public void map(LongWritable lineId, Text line, Mapper.Context context) {
            //Todo: split by tab like assignment2
            String[] words = line.toString().split("\\t");
            String head_word = words[0];
            //  String[] arrayString = new String[1];
            // arrayString[0] = head_word;
            try {
                //    head_word =  runpythonScript(arrayString);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String syntactic_ngram_String = words[1];
            long total_count;
            try {
                total_count = Long.parseLong(words[2]);
            } catch (NumberFormatException e) {
                return;
            }
            String[] syntactic_ngram_String_array = syntactic_ngram_String.split(" ");

            List<SyntacticNgram> synArray = new ArrayList<>();
            //SyntacticNgram[] synArray = new SyntacticNgram[syntactic_ngram_String_array.length];
            for (int i = 0; i < syntactic_ngram_String_array.length; i++) {
                String[] splitter = syntactic_ngram_String_array[i].split("/");
                //add here num of occurrences
                synArray.add(i,new SyntacticNgram(splitter[0], splitter[1], splitter[2], Long.parseLong(splitter[3]), total_count));
            }
            //now we have synarray that is simple way to look on what in the line:
//            todo: make DependencyPath -> how to do it? dependencytree?


            // this is a sort by using the default java interface
            SyntacticNgramComparator comparator = new SyntacticNgramComparator();
            synArray.sort(comparator);
            //here we are going to create a path and on the fly to send it if it's relevant (two nouns)
            List<List<SyntacticNgram>> typeInSentencesTree = new ArrayList<>();
            for (int i=0; i< (synArray.get(synArray.size() -1).position.intValue()+ 1) ; i++ ) {
                typeInSentencesTree.add (new ArrayList<>());
            }
            for (int i = 0; i < synArray.size(); i++) {
                typeInSentencesTree.get(synArray.get(i).position.intValue()).add(synArray.get(i));
            }
            for (int i = 0; i < typeInSentencesTree.size(); i++) {
                for (int j = 0; j < typeInSentencesTree.get(i).size(); j++) {
                    ArrayList<SyntacticNgram> listForDFS = new ArrayList<>();
                    try {
                        DFSSyntatticNgram(typeInSentencesTree, listForDFS, typeInSentencesTree.get(i).get(j).position.intValue(), j, j, typeInSentencesTree.get(i).get(j), context, total_count);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        private static Text CreateText(ArrayList<SyntacticNgram> path) {
            String stToText = "";
            for (int i = 0; i < path.size(); i++) {
                stToText += path.get(i).type + "/" + path.get(i).typeInSentence + " ";
            }
            return (new Text(stToText));
        }

        private static void DFSSyntatticNgram(List<List<SyntacticNgram>> typeInSentencesTree, ArrayList<SyntacticNgram> path, int lastLevel, int CurrentindexInLevel, int currentLevel, SyntacticNgram lastSyn, Mapper.Context context, long numOfOccurrences) throws Exception {
            if (currentLevel >= lastLevel) {
                path.add(lastSyn);
                if (path.size() >= 1) {
                    String[] arrayString = new String[1];
                    arrayString[0] = path.get(0).head_word;
                    String[] arrayString2 = new String[1];
                    arrayString2[0] = lastSyn.head_word;
                    context.write(new DependencyPath(CreateText(path),new LongWritable(numOfOccurrences)),
                            new NounPair(path.get(0).head_word,lastSyn.head_word));
                            //new NounPair(runpythonScript(arrayString), runpythonScript(arrayString2), numOfOccurrences));
                    return;
                }
            }
            if (typeInSentencesTree.get(currentLevel).size() == 0)
            //if need to skip for next one because no level 2 for example
            {
                DFSSyntatticNgram(typeInSentencesTree, path, lastLevel, CurrentindexInLevel, currentLevel + 1, lastSyn, context, numOfOccurrences);

            }
            if (!path.isEmpty() && path.get(path.size() - 1).position >= currentLevel) {
                //no need to add it, just skip for next level
                DFSSyntatticNgram(typeInSentencesTree, path, lastLevel, CurrentindexInLevel, currentLevel + 1, lastSyn, context, numOfOccurrences);

            } else {
                path.add(typeInSentencesTree.get(currentLevel).get(CurrentindexInLevel));
                DFSSyntatticNgram(typeInSentencesTree, path, lastLevel, 0, currentLevel + 1, lastSyn, context, numOfOccurrences);
                path.remove(typeInSentencesTree.get(currentLevel).get(CurrentindexInLevel));
                if (CurrentindexInLevel < typeInSentencesTree.get(currentLevel).size() - 1)
                    DFSSyntatticNgram(typeInSentencesTree, path, lastLevel, CurrentindexInLevel + 1, currentLevel, lastSyn, context, numOfOccurrences);
            }
        }

        @Override
        protected void cleanup(Mapper<LongWritable, Text, DependencyPath, NounPair>.Context context) throws IOException, InterruptedException {
            System.out.println("done map");
        }
    }

    public static class PartitionerClass extends Partitioner<DependencyPath, NounPair> {

        @Override
        public int getPartition(DependencyPath key, NounPair value, int numPartitions) {
            return (key.hashCode() & 0xFFFFFFF) % numPartitions; // Make sure that equal occurrences will end up in same reducer
        }
    }

    public static class ReducerClass extends Reducer<DependencyPath, NounPair, DependencyPath, Text> {
        private long DPmin;

            private Counter featureLexiconSizeCounter ;

            @Override
            public void setup(Context context) {
                DPmin = context.getConfiguration().getLong("DPmin", 5);
                featureLexiconSizeCounter = context.getCounter(CounterType.FEATURE_LEXICON);
            }


        @Override
        public void reduce(DependencyPath path, Iterable<NounPair> occurrencesList, Context context)
                throws IOException, InterruptedException {
            StringBuilder valueString = new StringBuilder();
            long counter = 0;
            for (NounPair value : occurrencesList) {
                counter++;
                valueString.append(value.toString()).append("\t");

                }
                if(counter >=  DPmin) {
                    featureLexiconSizeCounter.increment(1);
                    path.setIdInVector(featureLexiconSizeCounter.getValue());
                    context.write(path, new Text(valueString.substring(0, valueString.length() - 1)));
                }
        }
    }

}






