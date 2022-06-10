package co.edu.unincca.is.test.testing3;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import co.edu.unincca.is.engine.Assessment;
import co.edu.unincca.is.engine.InferenceEngine;
import co.edu.unincca.is.fact.FactValue;
import co.edu.unincca.is.fact.FactValueType;
import co.edu.unincca.is.leaf.Node;
import co.edu.unincca.is.rule.RuleSetParser;
import co.edu.unincca.is.rule.RuleSetReader;
import co.edu.unincca.is.rule.RuleSetScanner;
import co.edu.unincca.is.test.util.NodeObject_For_Inference_Test;

public class WeddingPlanner_Inference_Test_3 {

	public static void main(String[] args) throws IOException {
		
		HashMap<String, NodeObject_For_Inference_Test> nameMap = new HashMap<>();
		BufferedReader br = new BufferedReader(new InputStreamReader(WeddingPlanner_Inference_Test_3.class.getResourceAsStream("Wedding_Planner Inference Test.txt")));
		String line;
		while((line = br.readLine()) != null)
		{
			String[]lineArray = line.split("-");
			String[]lineArraySecondLevel = lineArray[1].split(":");
			NodeObject_For_Inference_Test nfit = new NodeObject_For_Inference_Test(lineArray[0],lineArraySecondLevel);
			nameMap.put(lineArray[0], nfit);
		}
		br.close();
		
		RuleSetReader ilr = new RuleSetReader();
		ilr.setStreamSource(WeddingPlanner_Inference_Test_3.class.getResourceAsStream("Wedding Planner.txt"));
		RuleSetParser isf = new RuleSetParser();		
		RuleSetScanner rsc = new RuleSetScanner(ilr,isf);
		rsc.scanRuleSet();
		rsc.establishNodeSet(null);
		InferenceEngine ie = new InferenceEngine(isf.getNodeSet());
		Assessment ass = new Assessment(isf.getNodeSet(), isf.getNodeSet().getNodeSortedList().get(0).getNodeName());
//		Scanner scan = new Scanner(System.in);
		int i = 0;
		while(ie.getAssessmentState().getWorkingMemory().get(isf.getNodeSet().getNodeSortedList().get(0).getNodeName())==null)
		{
			
			Node nextQuestionNode = ie.getNextQuestion(ass);
			HashMap<String, FactValueType> questionFvtMap = ie.findTypeOfElementToBeAsked(nextQuestionNode);
			
			FactValueType fvt = null;
			String answer;
			
			for(String question: ie.getQuestionsFromNodeToBeAsked(nextQuestionNode))
			{
				System.out.println("questionFvt :"+questionFvtMap.get(question));
				System.out.println("Question: " + question+"?");

				if(i < 3)
				{
					answer = "true";
				}
				else
				{
					answer = nameMap.get(question).getValue();
				}
				System.out.println("Answer: "+answer);
//				String answer = scan.nextLine();			
				
				ie.feedAnswerToNode(nextQuestionNode, question, answer, questionFvtMap.get(question), ass);
				i++;
			}

			
		}
//		Stream<String> keyList = ie.getAssessmentState().getWorkingMemory().keySet().stream();
//		keyList.forEach(key -> {
//			System.out.println(key+" : "+ie.getAssessmentState().getWorkingMemory().get(key).getValue().toString());
//		});
		HashMap<String, FactValue> workingMemory = ie.getAssessmentState().getWorkingMemory();
		ie.getAssessmentState().getSummaryList().stream().forEachOrdered(node ->{
			System.out.println(node+" : "+workingMemory.get(node).getValue().toString());
		});
		
//		scan.close();
	}
}
