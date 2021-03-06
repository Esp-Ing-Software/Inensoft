package co.edu.unincca.is.rule;



import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import co.edu.unincca.is.fact.FactListValue;
import co.edu.unincca.is.fact.FactValue;
import co.edu.unincca.is.fact.FactValueType;
import co.edu.unincca.is.leaf.*;
import co.edu.unincca.is.engine.*;

/*
*
*  must comply with following rules;
*  1. 'A IS B' or 'A'  statement is a Value_conclsion Node
*  2. 'A IS CALC (B*(C - D) + E)' statement is a Expression_conclusion Node
*  3. 'A = B', 'A < B', 'A <= B', 'A > B' or 'A >= B' is a Comparison Node
*  4. statement not containing any keywords is a Boolean Node
*  5. any other statements containing 'INPUT' or 'FIXED' keyword are META node
*  
*  6. 'A = B' format cannot be a parent rule, in other words it must be a child statement.
*     However, 'A IS B' can be a parent so that if 'A = B' statement or item is written in a format of 'A IS B' then it can become a parent
*  	Note: if a statement is in a form of 'A is B' it is different from 'A IS B' format, and it will be a Boolean Node
*  7. Expression_conclusion Node must have a child statement containing 'NEED'(translated as 'AND') and/or 'WANT'(translated as 'OR') keyword.
*  
*  8. Comparison Node can not be a parent statement.
*  
*  9. Meta statement pattern matching is as follows;
*     9.1 ULU (U-'FIXED', L-'the gender-x is accepted', U-'IS FALSE')
*     9.2 ULUY (U-'FIXED', L-'the legislation start date', U-'IS', Y-'1/1/1988')
*     9.3 ULULOY (U- 'FIXED' L- 'the', U- 'DOB', L- 'must be', O- '>=', Y- '01/01/1988'
*     9.3 ULU (U- 'INPUT', L- 'the boy's name', U- 'AS TEXT')
*         Note: 'INPUT' type indicates which type of its value is, and the type is stated after keyword 'AS'


*/

public class RuleSetParser implements IScanFeeder {

//	enum LineType {META, VALUE_CONCLUSION, EXPR_CONCLUSION, COMPARISON, WARNING}
	/*
	 * patterns are as follows;
	 * U : upper case
	 * L : lower case
	 * M : mixed case
	 * No: number
	 * Da: date
	 * Ha: hash
	 * Url: url
	 * Id: UUID
	 * C : CALC (this is a keyword)
	 * De: decimal
	 * Q : quotation mark
	 * 
	 */
	
	final Pattern META_PATTERN_MATCHER = Pattern.compile("(^U)([MLU]*)([(No)(Da)ML(De)(Ha)(U(rl)?)(Id)]*$)");
	final Pattern VALUE_MATCHER = Pattern.compile("(^[LM]+)(U)?([MLQ(No)(Da)(De)(Ha)(Url)(Id)]*$)(?!C)");
	final Pattern EXPRESSION_CONCLUSION_MATCHER = Pattern.compile("(^[LM(Da)]+)(U)(C)");
	final Pattern COMPARISON_MATCHER = Pattern.compile("(^[MLU(Da)]+)(O)([MLUQ(No)(Da)(De)(Ha)(Url)(Id)]*$)");
	final Pattern ITERATE_MATCHER = Pattern.compile("(^[MLU(No)(Da)]+)(I)([MLU]+$)");
	final Pattern WARNING_MATCHER = Pattern.compile("WARNING");
	LineType matchTypes[] = LineType.values();
	NodeSet nodeSet = new NodeSet();
	List<Dependency> dependencyList = new ArrayList<>();
	
	@Override
	public void handleParent(String parentText, int lineNumber) {
		
		Node data = nodeSet.getNodeMap().get(parentText);
		
		if(data == null)
		{
			
//			valueConclusionMatcher = Pattern.compile("(^[LM]+)(U)?([MLQ(No)(Da)(De)(Ha)(Url)(Id)]*$)(?!C)"); // parent statement must not have operators in the middle of the statement, hence there is no 'O' of Token.tokenString in the regex.
			 
			 
			Tokens tokens = Tokenizer.getTokens(parentText);
			
			Pattern matchPatterns[] = {META_PATTERN_MATCHER, VALUE_MATCHER, EXPRESSION_CONCLUSION_MATCHER, WARNING_MATCHER};
			Pattern p;
			Matcher matcher;
			for(int i = 0; i < matchPatterns.length; i++) {
				
				p =  matchPatterns[i];
				matcher = p.matcher(tokens.tokensString);
				if(matcher.find() == true) {
					switch(i) {
						case 3:  //warningMatcher case
							handleWarning(parentText);
							break;
						case 0:  //metaMatcher case
							data = new MetadataLine(parentText, tokens);
							
							if(data.getFactValue().getValue().equals("WARNING"))
							{
								handleWarning(parentText);
							}
							break;
						case 1:  //valueConclusionMatcher case
							data = new ValueConclusionLine(parentText, tokens);
							
							if(matcher.group(2) != null 
								|| (tokens.tokensString.equals("L") || tokens.tokensString.equals("LM") || tokens.tokensString.equals("ML") || tokens.tokensString.equals("M")))
							{
								String variableName = data.getVariableName();
								Node tempNode = data;
								/*
								 * following lines are to look for any nodes having a its nodeName with any operators due to the reason that
								 * the node could be used to define a node previously used as a child node for other nodes
								 */
								List<String> possibleParentNodeKeyList = nodeSet.getNodeMap().keySet().stream().filter(key -> key.matches("(.+)?(\\s[<>=]+\\s?)?("+variableName+")(\\s[<>=]+)*(.(?!(IS)))*(.*(IS IN LIST).*)*")).collect(Collectors.toList());
								if(!possibleParentNodeKeyList.isEmpty())
								{
									possibleParentNodeKeyList.stream().forEachOrdered(item -> {
										this.dependencyList.add(new Dependency(nodeSet.getNodeMap().get(item), tempNode, DependencyType.getOr())); //Dependency Type :OR
									});
								}
							}	
							if(data.getFactValue().getValue().equals("WARNING"))
							{
								handleWarning(parentText);
							}
							break;
						case 2: //exprConclusionMatcher case 
							data = new ExprConclusionLine(parentText, tokens);
							
							String variableName = data.getVariableName();
							Node tempNode = data;
							/*
							 * following lines are to look for any nodes having a its nodeName with any operators due to the reason that
							 * the exprConclusion node could be used to define another node as a child node for other nodes if the variableName of exprConclusion node is mentioned somewhere else.
							 * However, it is excluding nodes having 'IS' keyword because if it has the keyword then it should have child nodes to define the node otherwise the entire rule set has NOT been written in correct way
							 */
							List<String> possibleParentNodeKeyList = nodeSet.getNodeMap().keySet().stream().filter(key -> key.matches("(.+)?(\\s[<>=]+\\s?)?("+variableName+")(\\s[<>=]+)*(.(?!(IS)))*(.*(IS IN LIST).*)*")).collect(Collectors.toList());
							if(!possibleParentNodeKeyList.isEmpty())
							{
								possibleParentNodeKeyList.stream().forEachOrdered(item -> {
									this.dependencyList.add(new Dependency(nodeSet.getNodeMap().get(item), tempNode, DependencyType.getOr())); //Dependency Type :OR
								});
							}
							if(data.getFactValue().getValue().equals("WARNING"))
							{
								handleWarning(parentText);
							}
							break;
						default:
							handleWarning(parentText);
							break;				
					
					}
					data.setNodeLine(lineNumber);
					if(data.getLineType().equals(LineType.META))
					{
						if(((MetadataLine)data).getMetaType().equals(MetaType.INPUT))
						{
							this.nodeSet.getInputMap().put(data.getVariableName(), data.getFactValue());
						}
						else if(((MetadataLine)data).getMetaType().equals(MetaType.FIXED))
						{
							this.nodeSet.getFactMap().put(data.getVariableName(), data.getFactValue());
						}
					}
					else
					{
						this.nodeSet.getNodeMap().put(data.getNodeName(), data);
						this.nodeSet.getNodeIdMap().put(data.getNodeId(), data.getNodeName());
					}
					break;
				}
			}	 
		}			
	}

	@SuppressWarnings("unlikely-arg-type")
	@Override
	public void handleChild(String parentText, String childText, String firstKeywordsGroup, int lineNumber) {
		/*
		 * the reason for using '*' at the last group of pattern within comparison is that 
		 * the last group contains No, Da, De, Ha, Url, Id. 
		 * In order to track more than one character within the square bracket of last group '*'(Matches 0 or more occurrences of the preceding expression) needs to be used.
		 * 
		 */
		int dependencyType = 0; 
		
		// is 'ITEM' child line
		if(childText.matches("(ITEM)(.*)"))
		{
			if(!parentText.matches("(.*)(AS LIST)"))
			{
				handleWarning(childText);
				return;
			}
			
			// is an indented item child
			childText = childText.replaceFirst("ITEM", "").trim();
			MetaType metaType = null;
			if(parentText.matches("^(INPUT)(.*)"))
			{
				metaType = MetaType.INPUT;
			}
			else if(parentText.matches("^(FIXED)(.*)"))
			{
				metaType = MetaType.FIXED;
			}
			handleListItem(parentText, childText, metaType);
		}
		else  // is 'A-statement', 'A IS B', 'A <= B', or 'A IS CALC (B * C)' child line
		{
			if(firstKeywordsGroup.matches("^(AND\\s?)(.*)")) 
			{
				dependencyType = handleNotKnownManOptPos(firstKeywordsGroup, DependencyType.getAnd()); // 8-AND | 1-KNOWN? 2-NOT? 64-MANDATORY? 32-OPTIONALLY? 16-POSSIBLY? 
			}
			else if(firstKeywordsGroup.matches("^(OR\\s?)(.*)"))
			{
				dependencyType = handleNotKnownManOptPos(firstKeywordsGroup, DependencyType.getOr()); // 4-OR | 1-KNOWN? 2-NOT? 64-MANDATORY? 32-OPTIONALLY? 16-POSSIBLY? 
			}
			else if(firstKeywordsGroup.matches("^(WANTS)"))
			{
				dependencyType = DependencyType.getOr(); // 4-OR
			}
			else if(firstKeywordsGroup.matches("^(NEEDS)"))
			{
				dependencyType = DependencyType.getMandatory() | DependencyType.getAnd();  //  8-AND | 64-MANDATORY
			}
			
			
			/*
			 * the keyword of 'AND' or 'OR' should be removed individually. 
			 * it should NOT be removed by using firstToken string in Tokens.tokensList.get(0)
			 * because firstToken string may have something else. 
			 * (e.g. string: 'AND NOT ALL Males' name should sound Male', then Token string will be 'UMLM', and 'U' contains 'AND NOT ALL'.
			 * so if we used 'firstToken string' to remove 'AND' in this case as 'string.replace(firstTokenString)' 
			 * then it will remove 'AND NOT ALL' even we only need to remove 'AND' 
			 * 
			 */
			
			
			Node data = nodeSet.getNodeMap().get(childText);  
			Tokens tokens = Tokenizer.getTokens(childText);
			if(data == null)
			{
//				valueConclusionMatcher =Pattern.compile("(^U)([LMU(Da)(No)(De)(Ha)(Url)(Id)]+$)"); // child statement for ValueConclusionLine starts with AND(OR), AND MANDATORY(OPTIONALLY, POSSIBLY) or AND (MANDATORY) (NOT) KNOWN
							
				Pattern matchPatterns[] = { VALUE_MATCHER, COMPARISON_MATCHER, ITERATE_MATCHER, EXPRESSION_CONCLUSION_MATCHER, WARNING_MATCHER};
				
				
				Pattern p;
				Matcher matcher;
				Node tempNode;
				List<String> possibleChildNodeKeyList;
				
				for(int i = 0; i < matchPatterns.length; i++) {
					p = matchPatterns[i];
					matcher = p.matcher(tokens.tokensString);
					
					if(matcher.find() == true)
					{
						switch(i)
						{
							case 4:  // warningMatcher case
								handleWarning(childText);
								break;
							case 0:  // valueConclusionMatcher case
								data = new ValueConclusionLine(childText, tokens);
								
								tempNode = data;
								possibleChildNodeKeyList = nodeSet.getNodeMap().keySet().stream().filter(key -> key.matches("(^"+tempNode.getVariableName()+")(.(IS(?!(\\sIN LIST))).*)*")).collect(Collectors.toList());
														
								if(!possibleChildNodeKeyList.isEmpty())
								{
									possibleChildNodeKeyList.stream().forEachOrdered(item -> {
										this.dependencyList.add(new Dependency(tempNode, nodeSet.getNodeMap().get(item), DependencyType.getOr())); //Dependency Type :OR
									});
								}
								
								if(data.getFactValue().getValue().equals("WARNING"))
								{
									handleWarning(parentText);
								}
								break;
							case 1:  // comparisonMatcher case
								data = new ComparisonLine(childText, tokens);
								
								FactValueType rhsType = ((ComparisonLine)data).getRHS().getType();
								String rhsString = ((ComparisonLine)data).getRHS().getValue().toString();
								String lhsString = ((ComparisonLine)data).getLHS();
								tempNode = data;
								possibleChildNodeKeyList = rhsType.equals(FactValueType.STRING)? 
														nodeSet.getNodeMap().keySet().stream().filter(key -> key.matches("(^"+lhsString+")(.(IS(?!(\\sIN LIST))).*)*")|| key.matches("(^"+rhsString+")(.(IS(?!(\\sIN LIST))).*)*")).collect(Collectors.toList())
														:
														nodeSet.getNodeMap().keySet().stream().filter(key -> key.matches("(^"+lhsString+")(.(IS(?!(\\sIN LIST))).*)*")).collect(Collectors.toList());

								if(!possibleChildNodeKeyList.isEmpty())
								{
									possibleChildNodeKeyList.stream().forEachOrdered(item -> {
										this.dependencyList.add(new Dependency(tempNode, nodeSet.getNodeMap().get(item), DependencyType.getOr())); //Dependency Type :OR
									});
								}
								
								if(data.getFactValue().getValue().equals("WARNING"))
								{
									handleWarning(parentText);
								}
								break;
							case 2:  // comparisonMatcher case
								data = new IterateLine(childText, tokens);
								if(data.getFactValue().getValue().equals("WARNING"))
								{
									handleWarning(parentText);
								}
								break;
							case 3: //exprConclusionMatcher case
								data = new ExprConclusionLine(childText, tokens);
								
								/*
								 * In this case, there is no mechanism to find possible parent nodes.
								 * I have brought 'local variable' concept for this case due to it may massed up with structuring node dependency tree with topological sort
								 * If ExprConclusion node is used as a child, then it means that this node is a local node which has to be strictly bound to its parent node only.  
								 */
								
								if(data.getFactValue().getValue().equals("WARNING"))
								{
									handleWarning(parentText);
								}
								break;
								
						}
						data.setNodeLine(lineNumber);
						this.nodeSet.getNodeMap().put(data.getNodeName(), data);
						this.nodeSet.getNodeIdMap().put(data.getNodeId(), data.getNodeName());
						break;
					}
				}
			}
			
			this.dependencyList.add(new Dependency(this.nodeSet.getNode(parentText),data,dependencyType));
		}
	}
	
	
	@Override
	public void handleListItem(String parentText, String itemText, MetaType metaType) {
		Tokens tokens = Tokenizer.getTokens(itemText);
		FactValue fv;
		if(tokens.tokensString.equals("Da"))
		{
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/M/yyyy");
     		LocalDate factValueInDate = LocalDate.parse(itemText, formatter);
     		fv = FactValue.parse(factValueInDate);
		}
		else if(tokens.tokensString.equals("De"))
		{
			fv = FactValue.parse(Double.parseDouble(itemText));
		}
		else if(tokens.tokensString.equals("No"))
		{
			fv = FactValue.parse(Integer.parseInt(itemText));
		}
		else if(tokens.tokensString.equals("Ha"))
		{
			fv = FactValue.parseHash(itemText);
		}
		else if(tokens.tokensString.equals("Url"))
		{
			fv = FactValue.parseURL(itemText);
		}
		else if(tokens.tokensString.equals("Id"))
		{
			fv = FactValue.parseUUID(itemText);
		}
		else if(itemText.matches("FfAaLlSsEe")||itemText.matches("TtRrUuEe"))
		{
			fv =FactValue.parse(Boolean.parseBoolean(itemText));
		}
		else
		{
			fv = FactValue.parse(itemText);
		}
		String stringToGetFactValue = (parentText.substring(5, parentText.indexOf("AS"))).trim();
		if(metaType.equals(MetaType.INPUT))
		{
			((FactListValue<?>)this.nodeSet.getInputMap().get(stringToGetFactValue)).getValue().add(fv);
		}
		else if(metaType.equals(MetaType.FIXED))
		{
			((FactListValue<?>)this.nodeSet.getFactMap().get(stringToGetFactValue)).getValue().add(fv);
		}
	}
	
	
	@Override
	public NodeSet getNodeSet()
	{
		return this.nodeSet;
	}
	
	@Override
	public String handleWarning(String parentText)
	{
		return parentText+": rule format is not matched. Please check the format again";
	}
	
	/*
	 * this method is to create virtual nodes where a certain node has 'AND' or 'MANDATORY_AND', and 'OR' children at the same time.
	 * when a virtual node is created, all 'AND' children should be connected to the virtual node as 'AND' children
	 * and the virtual node should be a 'OR' child of the original parent node 
	 */
	public HashMap<String, Node> handlingVirtualNode(List<Dependency> dependencyList)
	{
		
		HashMap<String, Node> virtualNodeMap = new HashMap<>();
		

		nodeSet.getNodeMap().values().stream().forEachOrdered((node) ->{
			virtualNodeMap.put(node.getNodeName(), node);
			List<Dependency> dpList= dependencyList.stream()
							   .filter(dp -> node.getNodeName().equals(dp.getParentNode().getNodeName()))
							   .collect(Collectors.toList());
			
			/*
			 * need to handle Mandatory, optionally, possibly NodeOptions
			 */
			int and = 0;
			int mandatoryAnd = 0;
			int or = 0;
			if(!dpList.isEmpty())
			{
				for(Dependency dp: dpList) //can this for each loop be converted to dpList.stream().forEachOrdered() ?
				{
					if((dp.getDependencyType() & DependencyType.getAnd()) == DependencyType.getAnd())
					{
						and++;
						if(dp.getDependencyType() == (DependencyType.getMandatory() | DependencyType.getAnd()))
						{
							mandatoryAnd++;
						}
					}
					else if((dp.getDependencyType() & DependencyType.getOr()) == DependencyType.getOr())
					{
						or++;
					}
				}
				boolean hasAndOr = (and>0 && or>0)? true:false;  
				if(hasAndOr)
				{
					
					String parentNodeOfVirtualNodeName = node.getNodeName();
					Node virtualNode = new ValueConclusionLine("VirtualNode-"+parentNodeOfVirtualNodeName, Tokenizer.getTokens("VirtualNode-"+parentNodeOfVirtualNodeName));
					this.nodeSet.getNodeIdMap().put(virtualNode.getNodeId(), "VirtualNode-"+parentNodeOfVirtualNodeName);
					virtualNodeMap.put("VirtualNode-"+parentNodeOfVirtualNodeName, virtualNode);
					if(mandatoryAnd > 0)
					{
						dependencyList.add(new Dependency(node, virtualNode, (DependencyType.getMandatory() | DependencyType.getOr())));
					}
					else
					{
						dependencyList.add(new Dependency(node, virtualNode, DependencyType.getOr()));
					}
					dpList.stream()
						  .filter(dp -> dp.getDependencyType() == DependencyType.getAnd() || dp.getDependencyType() == (DependencyType.getMandatory() | DependencyType.getAnd()))
						  .forEachOrdered(dp -> dp.setParentNode(virtualNode));
				}
			}
			
		});
		return virtualNodeMap;
	}
	
	@Override
	public int[][] createDependencyMatrix()
	{
		this.nodeSet.setNodeMap(handlingVirtualNode(this.dependencyList));
		/*
		 * number of rule is not always matched with the last ruleId in Node 
		 */
		int numberOfRules = Node.getStaticNodeId();
		
		int[][] dependencyMatrix = new int[numberOfRules][numberOfRules];
	
		
		this.dependencyList.forEach(dp -> {
			int parentId = dp.getParentNode().getNodeId();
			int childId = dp.getChildNode().getNodeId();
			int dpType = dp.getDependencyType();
			dependencyMatrix[parentId][childId] = dpType;
		});
		
		return dependencyMatrix;
	}

	@Override
	public void setNodeSet(NodeSet ns)
	{
		this.nodeSet = ns;
	}
	
	private int handleNotKnownManOptPos(String firstTokenString, int dependencyType)
	{
		if(dependencyType != 0)
		{
			if(firstTokenString.contains("NOT"))
			{
				dependencyType |= DependencyType.getNot();
			}
			if(firstTokenString.contains("KNOWN"))
			{
				dependencyType |= DependencyType.getKnown();
			}
			if(firstTokenString.contains("MANDATORY"))
			{
				dependencyType |= DependencyType.getMandatory();
			}
			if(firstTokenString.contains("OPTIONALLY"))
			{
				dependencyType |= DependencyType.getOptional();
			}
			if(firstTokenString.contains("POSSIBLY"))
			{
				dependencyType |= DependencyType.getPossible();
			}
		}
		
		return dependencyType;
	}

}