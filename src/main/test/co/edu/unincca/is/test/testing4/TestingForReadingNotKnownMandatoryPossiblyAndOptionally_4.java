package co.edu.unincca.is.test.testing4;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import co.edu.unincca.is.rule.RuleSetParser;
import co.edu.unincca.is.rule.RuleSetReader;
import co.edu.unincca.is.rule.RuleSetScanner;
import co.edu.unincca.is.test.util.NodeObject_For_Inference_Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

public class TestingForReadingNotKnownMandatoryPossiblyAndOptionally_4 {

	public static void main(String[] args) throws IOException{
		
		RuleSetReader ilr = new RuleSetReader();
		ilr.setStreamSource(TestingForReadingNotKnownMandatoryPossiblyAndOptionally_4.class.getResourceAsStream("testing NOT, KNOWN, Mandatory, Possibly, and Optionally.txt"));
		RuleSetParser isf = new RuleSetParser();		
		RuleSetScanner rsc = new RuleSetScanner(ilr,isf);
		rsc.scanRuleSet();
		rsc.establishNodeSet(null);
		List<String> readLine = new ArrayList<>();
		isf.getNodeSet().getNodeSortedList().stream().forEachOrdered(node ->{
			readLine.add("nodeName: "+node.getNodeName());
			isf.getNodeSet().getDependencyMatrix().getToChildDependencyList(node.getNodeId()).stream().forEach(dep ->{
																													readLine.add("dependency type: "+isf.getNodeSet().getDependencyMatrix().getDependencyType(node.getNodeId(), isf.getNodeSet().getNodeMap().get(isf.getNodeSet().getNodeIdMap().get(dep)).getNodeId()));
																													readLine.add("dependency: "+isf.getNodeSet().getNodeIdMap().get(dep));
																												  }
																											); 
			
																		   }
																   );
		
		List<String> comparisonFileRead = new ArrayList<>();
		BufferedReader br = new BufferedReader(new InputStreamReader(TestingForReadingNotKnownMandatoryPossiblyAndOptionally_4.class.getResourceAsStream("Comparison File For Reading Not Known Man Op Pos file.txt")));
		String line;
		while((line = br.readLine()) != null)
		{
			comparisonFileRead.add(line);
		}
		br.close();

		if(readLine.size() == comparisonFileRead.size())
		{
			List<Integer> tempArray = IntStream.range(0, readLine.size()-1).filter(i -> !readLine.get(i).equals(comparisonFileRead.get(i))).boxed().collect(Collectors.toList());
			if(tempArray.isEmpty())
			{
				System.out.println("testing successful");
			}
			else
			{
				System.out.println("testing fail");
			}
		}
		else
		{
			System.out.println("testing fail");
		}
	}

}
