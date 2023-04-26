package TempOutput;

import entity.*;
import entity.dto.EnreDTO;
import entity.properties.Relation;
import org.json.JSONArray;
import org.json.JSONObject;
import util.EnreFormatParser;
import util.SingleCollect;
import util.Tuple;
import visitor.relationInf.RelationInf;

import java.util.*;

public class JsonString {

    public static HashMap<String, Integer> turnStrMap(String status){
        HashMap<String, Integer> nameTonum = new HashMap<>();
        String[] cells = status.split("\n");
        for(String cell : cells){
            String[] nameAnum = cell.split(": ");
            nameTonum.put(nameAnum[0], Integer.valueOf(nameAnum[1]));
        }
        return nameTonum;
    }

    /**
     * get current entity's file id
     * @param id
     * @return
     */
    public static int getCurrentFileId(int id){
        SingleCollect singleCollect = SingleCollect.getSingleCollectInstance();
        if(singleCollect.getEntityById(id) instanceof FileEntity){
            return id;
        }
        else if (singleCollect.getEntityById(singleCollect.getEntityById(id).getParentId()) instanceof FileEntity){
            return singleCollect.getEntityById(id).getParentId();
        }
        else {
            return getCurrentFileId(singleCollect.getEntityById(id).getParentId());
        }
    }

    public static EnreDTO jsonWriteRelation(Map<Integer, ArrayList<Tuple<Integer, Relation>>> relationMap, String hiddenPath, boolean slim) throws Exception {
        JSONObject obj = JSONWriteRelation(relationMap, hiddenPath, slim);
        return EnreFormatParser.parse(obj);
    }

    public static JSONObject JSONWriteRelation(Map<Integer, ArrayList<Tuple<Integer, Relation>>> relationMap, String hiddenPath, boolean slim) throws Exception {

        JSONObject obj=new JSONObject();//创建JSONObject对象

        SingleCollect singleCollect = SingleCollect.getSingleCollectInstance();
        ProcessHidden processHidden = ProcessHidden.getProcessHiddeninstance();
        if (hiddenPath != null){
            processHidden.convertCSV2DB(hiddenPath);
//            processHidden.outputConvertInfo("base-enre-out/hidden_convert.csv");
        }

        obj.put("schemaVersion","1");
        Iterator<BaseEntity> iterator = singleCollect.getEntities().iterator();

        List<JSONObject> subCategories = new ArrayList<>();
        for (String i : List.of("Package", "File", "Class", "Interface", "Annotation", "Enum", "Method", "Variable", "EnumConstant", "AnnotationTypeMember")){
            JSONObject cate = new JSONObject();
            cate.put("name", i);
            subCategories.add(cate);
        }
        obj.put("categories", subCategories);

        RelationInf relationInf = new RelationInf();
        JSONObject subEntities = new JSONObject();
        for(String entity : turnStrMap(relationInf.entityStatis()).keySet()){
            subEntities.put(entity, turnStrMap(relationInf.entityStatis()).get(entity));
        }
        obj.put("entityNum", subEntities);

        JSONObject subRelations = new JSONObject();
        for(String relation : turnStrMap(relationInf.dependencyStatis()).keySet()){
            subRelations.put(relation, turnStrMap(relationInf.dependencyStatis()).get(relation));
        }
        obj.put("relationNum", subRelations);

//        JSONObject subCKIndices = new JSONObject();
//        for (String index : singleCollect.getCkIndices().keySet()){
//            subCKIndices.put(index, singleCollect.getCk(index));
//        }
//        obj.put("CKIndices", subCKIndices);

        List<JSONObject> subObjVariable=new ArrayList<JSONObject>();

        while(iterator.hasNext()) {
            BaseEntity entity = iterator.next();
            JSONObject entityObj = new JSONObject();
            entityObj.put("id", entity.getId());
            entityObj.put("category", singleCollect.getEntityType(entity.getId()));
            entityObj.put("name", entity.getName());
            entityObj.put("qualifiedName", entity.getQualifiedName());
            entityObj.put("parentId", entity.getParentId());
            entityObj.put("external", false);
            if (entity.getRawType() != null){
                String raw = entity.getRawType();
                entityObj.put("rawType", processRawType(raw));
            }
            //AOSP HIDDEN API
//            JSONObject hiddenObj = new JSONObject();
//            hiddenObj.put("hidden", entity.getHidden());
//            hiddenObj.put("maxTargetSdk", entity.getMaxTargetSdk());
//            entityObj.accumulate("aosp_hidden", hiddenObj);
            //Modifiers
            if (!entity.getModifiers().isEmpty()){
                String m = "";
                for (String modifier : entity.getModifiers()){
                    if (!modifier.contains("@")){
                        m = m.concat(modifier + " ");
                    }
                }
                if (m.endsWith(" ")){
                    m = m.substring(0, m.length()-1);
                }
//                try {
//                    entityObj.put("modifiers", m.substring(0, m.length()-1));
//                }catch (StringIndexOutOfBoundsException e){
                entityObj.put("modifiers", m);
//                }

            }
            //entity File
            String entityFile;
            if (entity instanceof PackageEntity){
                entityFile = null;
            } else {
                entityFile = ((FileEntity) singleCollect.getEntityById(getCurrentFileId(entity.getId()))).getFullPath();
            }
            entityObj.put("File", entityFile);
            //variable kind
            if(entity instanceof VariableEntity){
                entityObj.put("global", ((VariableEntity) entity).getGlobal());
                if (!processHidden.getResult().isEmpty() && ((VariableEntity) entity).getGlobal() && processHidden.checkHidden((VariableEntity) entity)!= null){
                    entityObj.put("hidden", processHidden.checkHidden((VariableEntity) entity));
                }
            }
            //inner Type
            if(entity instanceof TypeEntity){
                if (!processHidden.getResult().isEmpty() && processHidden.checkHidden((TypeEntity)entity) != null){
                    entityObj.put("hidden", processHidden.checkHidden((TypeEntity)entity));
                }
                if (!((TypeEntity) entity).getInnerType().isEmpty()){
                    entityObj.put("innerType", ((TypeEntity) entity).getInnerType());
                }
                if (entity instanceof ClassEntity && ((ClassEntity) entity).getAnonymousRank() != 0){
                    entityObj.put("anonymousRank", ((ClassEntity) entity).getAnonymousRank());
                    entityObj.put("anonymousBindVar", ((ClassEntity) entity).getAnonymousBindVar());
                }
            }
            //location
            if (!slim){
                if (!(entity instanceof FileEntity || entity instanceof PackageEntity)){
                    JSONObject locObj = new JSONObject();
                    locObj.put("startLine", entity.getLocation().getStartLine());
                    locObj.put("endLine", entity.getLocation().getEndLine());
                    locObj.put("startColumn", entity.getLocation().getStartColumn());
                    locObj.put("endColumn", entity.getLocation().getEndColumn());
                    entityObj.accumulate("location", locObj);
                }
            }
            //method parameter Type
            if (entity instanceof MethodEntity){
                String parType = "";
                String parName = "";
                if (! ((MethodEntity) entity).getParameters().isEmpty()){
                    for (int parId : ((MethodEntity) entity).getParameters()){
                        parType = parType.concat(processRawType(singleCollect.getEntityById(parId).getRawType()) + " ");
                        parName = parName.concat(singleCollect.getEntityById(parId).getName() + " ");
                    }
                    parType = parType.substring(0, parType.length()-1);
                    parName = parName.substring(0, parName.length()-1);
                }
                JSONObject parObj = new JSONObject();
                parObj.put("names", parName);
                parObj.put("types", parType);
                entityObj.accumulate("parameter", parObj);
                if (!processHidden.getResult().isEmpty() && processHidden.checkHidden((MethodEntity)entity, parType)!= null){
                    entityObj.put("hidden", processHidden.checkHidden((MethodEntity)entity, parType));
                }

                //dependency enhancement
                if (!slim && ((MethodEntity) entity).getIndices() != null){
                    JSONObject enhanceObj = new JSONObject();
                    enhanceObj.put("isOverride", ((MethodEntity) entity).getIndices().getIsOverride());
                    enhanceObj.put("isSetter", ((MethodEntity) entity).getIndices().getIsSetter());
                    enhanceObj.put("isGetter", ((MethodEntity) entity).getIndices().getIsGetter());
                    enhanceObj.put("isDelegator", ((MethodEntity) entity).getIndices().getIsDelegator());
                    enhanceObj.put("isRecursive", ((MethodEntity) entity).getIndices().getIsRecursive());
                    enhanceObj.put("isPublic", ((MethodEntity) entity).getIndices().getIsPublic());
                    enhanceObj.put("isStatic", ((MethodEntity) entity).getIndices().getIsStatic());
                    enhanceObj.put("isSynchronized", ((MethodEntity) entity).getIndices().getIsSynchronized());
                    enhanceObj.put("isConstructor", ((MethodEntity) entity).isConstructor());
                    enhanceObj.put("isAbstract", ((MethodEntity) entity).getIndices().getMethodIsAbstract());
                    entityObj.accumulate("enhancement", enhanceObj);
                }
            }
            //bin path
            if (entity.getBinPath()!= null){
                JSONObject binObj = new JSONObject();
                binObj.put("binPath", entity.getBinPath().getL());
                binObj.put("binNum", entity.getBinPath().getR());
                entityObj.accumulate("additionalBin", binObj);
            }

            subObjVariable.add(entityObj);
        }

        if (!slim){
            for (ExternalEntity externalEntity : singleCollect.getExternalEntities()) {
                JSONObject external = new JSONObject();
                external.put("qualifiedName", externalEntity.getQualifiedName());
                external.put("external", true);
                external.put("name", externalEntity.getName());
                external.put("id", externalEntity.getId());
//            if (externalEntity.getType().equals(Configure.EXTERNAL_ENTITY_METHOD)){
//                external.put("returnType", externalEntity.getReturnType());
//            }
                subObjVariable.add(external);
            }
        }

        obj.put("variables",subObjVariable);


        for(int fromEntity:relationMap.keySet()) {
            for(Tuple<Integer,Relation> toEntityObj:relationMap.get(fromEntity)) {
                    int toEntity=toEntityObj.getL();

//                for(Relation type : relationMap.get(fromEntity).get(toEntity)) {
                    Relation type = toEntityObj.getR();
                    if(type.getKind().contains("by")){
                        continue;
                    }
                    JSONObject subObj=new JSONObject();//创建对象数组里的子对象

//                    JSONObject srcObj = new JSONObject();
//                    srcObj.put("id", fromEntity);
//                    srcObj.put("type", singleCollect.getEntityType(fromEntity));
//                    srcObj.put("name", singleCollect.getEntityById(fromEntity).getName());
//                    srcObj.put("qualified name", singleCollect.getEntityById(fromEntity).getQualifiedName());
//                    srcObj.put("parentId", singleCollect.getEntityById(fromEntity).getParentId());
//                    srcObj.put("childrenIds", singleCollect.getEntityById(fromEntity).getChildrenIds());

                    subObj.put("src",fromEntity);
                    subObj.put("dest",toEntity);
//                    subObj.accumulate("src", srcObj);
//   l                 subObj.accumulate("dest", destObj);

                    JSONObject reObj=new JSONObject();//创建对象数组里的子对象
                    reObj.put(type.getKind(), 1);
                    if (type.getBindVar() != -1){
                        reObj.put("bindVar", type.getBindVar());
                    }
                    if (type.getModifyAccessible()){
                        reObj.put("modifyAccessible", true);
                    }
                    if (!type.getArguemnts().isEmpty()){
                        String args = "";
                        for (String arg: type.getArguemnts()){
                            args = args.concat(arg.replace("\"", "").replace(",", " ") + " ");
                        }
                        args = args.substring(0, args.length()-1);
                        reObj.put("arguments", args);
                    }
                    if (type.getInvoke()){
//                        try{
//                            JSONObject locObj = new JSONObject();
//                            locObj.put("startLine", type.getLocation().getStartLine());
//                            locObj.put("endLine", type.getLocation().getEndLine());
//                            locObj.put("startColumn", type.getLocation().getStartColumn());
//                            locObj.put("endColumn", type.getLocation().getEndColumn());
//                            reObj.accumulate("invoke", locObj);
//                        } catch (NullPointerException e){
                            reObj.put("invoke", true);
//                        }
                    }
//                    else {
                    if (!slim){
                        JSONObject locObj = new JSONObject();
                        locObj.put("startLine", type.getLocation().getStartLine());
                        locObj.put("endLine", type.getLocation().getEndLine());
                        locObj.put("startColumn", type.getLocation().getStartColumn());
                        locObj.put("endColumn", type.getLocation().getEndColumn());
                        reObj.accumulate("loc", locObj);
                    }
//                    }
                    subObj.accumulate("values",reObj);
                    obj.accumulate("cells",subObj);

//                }

            }
        }
        /**
         * Output not match hidden
         */
//        if (hiddenPath != null) {
//            processHidden.outputResult();
//        }
        return obj;
    }

    public static String JSONWriteEntity(List<BaseEntity> entityList) throws Exception {


        JSONObject obj=new JSONObject();//创建JSONObject对象

        obj.put("schemaVersion","1");

        List<String> subObjVariable=new ArrayList<String>();//创建对象数组里的子对象
        for(BaseEntity en:entityList) {
            subObjVariable.add(en.toString());
        }
        obj.put("variables",subObjVariable);

        return obj.toString();
    }

    public static String processRawType (String rawType){
        if (rawType == null){
            return null;
        }
//        else {
//            if (rawType.contains("<")){
////                String[] temp = rawType.split("<");
////                rawType = processRawType(temp[0]).concat("-"+processRawType(temp[1]));
//                rawType = rawType.replaceAll("<", "&gt;");
//            }
//            if (rawType.contains(">")){
//                rawType = rawType.replaceAll(">", "\\>");
//            }
//            if (rawType.contains("[")){
////                String[] temp = rawType.split("\\[");
////                rawType = processRawType(temp[0]).concat("-"+processRawType(temp[1]));
//                rawType = rawType.replaceAll("\\[", "\\[");
//            }
//            if (rawType.contains("]")){
//                rawType = rawType.replaceAll("]", "\\]");
//            }
//            if (rawType.contains(",")){
////                String[] temp = rawType.split(",");
////                rawType = processRawType(temp[0]).concat("-"+processRawType(temp[1]));
//                rawType = rawType.replaceAll(",", "\\,");
//            }
//            if (rawType.contains("java")){
//                String[] temp = rawType.split("\\.");
//                rawType = temp[temp.length - 1];
//            }
//            rawType = StringEscapeUtils.unescapeJava(rawType);
//        }
        return rawType;
    }
    //与JArcher适配的json格式输出
    public static String JSONWriteRelationJA(Map<Integer, ArrayList<Tuple<Integer, Relation>>> relationMap,String rootDir,String projectName,String lang) throws Exception {

        JSONObject obj=new JSONObject(new HashMap<>());//创建JSONObject对象
        //singleCollect包含所有实体和依赖信息，processHidden在hidden模式下才执行
        SingleCollect singleCollect = SingleCollect.getSingleCollectInstance();
        //模式版本参数，暂未维护
        obj.put("schemaVersion","1");
        obj.put("Name",projectName);
        obj.put("lang",lang);
        obj.put("rootDir",rootDir);
        //获取实体列表,iterator迭代器不暴露集合内部实现的情况下遍历集合元素的方法
        Iterator<BaseEntity> iterator = singleCollect.getEntities().iterator();
        //存放实体文件
        List<String> variableFiles=new ArrayList<>();
        List<JSONObject> subObjVariable=new ArrayList<JSONObject>();
        //ja需要的类型的实体。
        List<String> JAEntityTypes=List.of("Variable","Class","Method","Interface","Enum","Annotation","Annotation Member");
        List<Integer> JAEntityList=new ArrayList<>();
        //实体的详情信息：
        while(iterator.hasNext()) {
            BaseEntity entity = iterator.next();
            String type=singleCollect.getEntityType(entity.getId());
            //排除掉不需要实体类型Package,TypeParameter(奇怪的是enre输出的实体列表中也没有TypeParameter类实体，统计中却有)
            if(!JAEntityTypes.contains(type)){
                continue;
            }
            //true表示是有序json,new LinkedHashMap()按照属性的hash而不是按照值的hash
            JSONObject entityObj = new JSONObject(new LinkedHashMap<String,String>());
            //存储在实体列表中的索引
            JAEntityList.add(entity.getId());

            //entity type
            entityObj.put("type",type);
            if(type.equals("Variable")){
                entityObj.put("extendVarType",type);//instanceVAr,ClassVar,LocalVar;
                type="Var";
                entityObj.put("type","Var");
            }else if(type.equals("Class")||type.equals("Interface")||type.equals("Enum")){
                entityObj.put("ExtendClassType",type);
                entityObj.put("typeIsAbstract",false);
                //有一个indices属性组有typeIsAbstract属性，但是method实体才有indices属性
                type="Type";
                entityObj.put("type","Type");
            }else if(type.equals("Method")){
                type="FuncImpl";
                entityObj.put("type","FuncImpl");
                entityObj.put("methodIsAbstract",false);
            }else if(type.equals("Annotation")){
                entityObj.put("type","Type");
            }else if(type.equals("Annotation Member")){
                entityObj.put("type","FuncProto");
            }
            //put会覆盖原有属性，accumulate会在对应属性累计增加
            if (entity.getRawType() != null){
                String raw = entity.getRawType();
                entityObj.put("rawType",processRawTypeJA(raw,type));
            }
            Boolean varIsStatic=false;
            //Modifiers，实体的修饰符
            if (!entity.getModifiers().isEmpty()){
                for (String modifier : entity.getModifiers()){
                    if(modifier.equals("abstract")&&type.equals("Type")){
                        //put会覆盖原本属性
                        entityObj.put("typeIsAbstract",true);
                        break;
                    }
                    if(modifier.equals("abstract")&&type.equals("FuncImpl")){
                        entityObj.put("methodIsAbstract",true);
                        break;
                    }
                    if(modifier.equals("static")&&type.equals("Var")){
                        varIsStatic=true;
                        break;
                    }
                }
            }
            //qualifiedname和ja中的object还有些不同，qualifiedname是点连接的包路径，object当实体为文件类型时，会变成src开始以‘/’连接的路径
            String object=entity.getQualifiedName();
            //entity File
            String entityFile;
            entityFile = ((FileEntity) singleCollect.getEntityById(getCurrentFileId(entity.getId()))).getFullPath();
            entityObj.put("file", entityFile);
            int srcFileIndex=variableFiles.indexOf(entityFile);
            if(srcFileIndex<0){
                variableFiles.add(entityFile);
            }
//            variableFiles.add(entityFile);
            //当实体为文件类型时，会变成从src开始以‘/’连接的路径
            if(entity instanceof FileEntity) object=entityFile;
            entityObj.put("index",JAEntityList.indexOf(entity.getId()));
            entityObj.put("object",object);
            if(entity instanceof VariableEntity){
                //增加extendVarType属性，根据在类文件中的位置（是在方法体中还是在类），和在类中是否有static修饰符
                //通过父实体的类型判断位置
                String pType=singleCollect.getEntityType(entity.getParentId());
                if(pType.equals("Class")||pType.equals("Enum")||pType.equals("Interface")){
                    if(varIsStatic){
                        entityObj.put("extendVarType","ClassVar");//类变量。类中方法体外的变量
                    }else entityObj.put("extendVarType","InstanceVar");//成员变量。静态的类中变量
                }else if(pType.equals("Method")){
                    entityObj.put("extendVarType","LocalVar");//局部变量。方法体中变量
                }
            }
            //location，ja中的文件类型实体中的location用空表示
            if (!(entity instanceof FileEntity || entity instanceof PackageEntity)){
                JSONObject locObj = new JSONObject();
                locObj.put("line", entity.getLocation().getStartLine());
                locObj.put("row", entity.getLocation().getStartColumn());
                entityObj.accumulate("location", locObj);
                //ja只用line row，
            }else if(entity instanceof FileEntity){
                JSONObject locObj = new JSONObject();
                entityObj.accumulate("location", locObj);
            }

            //关于var,class的增强属性未添加
            //method parameter Type
            if (entity instanceof MethodEntity){
                //ja中对method的增强属性是直接放在entity属性中的，不用enhancement打包。
                //dependency enhancement
                if (((MethodEntity) entity).getIndices() != null){
                    entityObj.accumulate("isOverride", ((MethodEntity) entity).getIndices().getIsOverride());
                    entityObj.accumulate("isSetter", ((MethodEntity) entity).getIndices().getIsSetter());
                    entityObj.accumulate("isGetter", ((MethodEntity) entity).getIndices().getIsGetter());
                    entityObj.accumulate("isDelegator", ((MethodEntity) entity).getIndices().getIsDelegator());
                    entityObj.accumulate("isRecursive", ((MethodEntity) entity).getIndices().getIsRecursive());
                    entityObj.accumulate("isPublic", ((MethodEntity) entity).getIndices().getIsPublic());
                    entityObj.accumulate("isStatic", ((MethodEntity) entity).getIndices().getIsStatic());
                    entityObj.accumulate("isSynchronized", ((MethodEntity) entity).getIndices().getIsSynchronized());
                    entityObj.accumulate("isConstructor", ((MethodEntity) entity).isConstructor());
                    entityObj.accumulate("isAbstract", ((MethodEntity) entity).getIndices().getMethodIsAbstract());
                    //assign方法，是否对成员变量进行赋值。发现method实体的indecs属性数组中有isAssign参数，且在visitor/processEntity.java中有judge判断属性方法。
                    entityObj.accumulate("isAssign",((MethodEntity) entity).getIndices().getIsAssign());
                    //isCallSuper,在visitor/deper/CallBf.java文件中增加判断逻辑。发现method实体的indecs属性数组中有isCallSuper参数
                    entityObj.accumulate("isCallSuper",((MethodEntity) entity).getIndices().getIsCallSuper());
                }
            }
            //bin path，ja中不需要检测存储仓库
            subObjVariable.add(entityObj);
        }
        //依赖列表，改造思路按照实体依赖两端实体所在的文件，建立文件之间的依赖。
        int edgeNum=0;
        JSONArray allCells=new JSONArray();
        //relationMap类型:Map<Integer, ArrayList<Tuple<Integer, Relation>>>
        //integer是源实体id
        //Relation类中存放类型kind，toEntity目标实体id，location-实体在定义的文件的位置？。
        obj.put("cells",new JSONArray());
        for(int fromEntity:relationMap.keySet()) {
            //获取实体类型
            String srcType=singleCollect.getEntityType(fromEntity);
            //获取实体所在文件File
            String srcFile;
            if (!JAEntityTypes.contains(srcType)){
                continue;//忽略不需要的实体的依赖边。
            } else {
                //获取源实体
                BaseEntity srcEntity=singleCollect.getEntityById(fromEntity);
                srcFile = ((FileEntity) singleCollect.getEntityById(getCurrentFileId(srcEntity.getId()))).getFullPath();
            }
            //获取源文件在variable里的位置
            int srcFileIndex=variableFiles.indexOf(srcFile);
            if(srcFileIndex<0){
                srcFileIndex=variableFiles.size();
                variableFiles.add(srcFile);
            }
            //目标数组用于判断是否src->dest的cell对象已经被创建
            List<Integer> destList=new ArrayList<>();
            for(Tuple<Integer,Relation> toEntityObj:relationMap.get(fromEntity)) {
                Relation relation = toEntityObj.getR();//relation对象
                if(relation.getKind().contains("by")){
                    continue;
                }
//                if(!JACellTypes.contains(relation.getKind())){
//                    continue;
//                }
                int toEntity=toEntityObj.getL();//目标实体id
                //获取目标实体类型
                String destType= singleCollect.getEntityType(toEntity);
                //获取目标实体所在文件File
                String destFile;
                if (!JAEntityTypes.contains(destType)){
                    continue;
                } else {
                    //获取目标实体
                    BaseEntity destEntity=singleCollect.getEntityById(toEntity);
                    destFile = ((FileEntity) singleCollect.getEntityById(getCurrentFileId(destEntity.getId()))).getFullPath();
                }
                //获取目标文件在variable里的位置
                int destFileIndex=variableFiles.indexOf(destFile);
                if(destFileIndex<0){
                    destFileIndex=variableFiles.size();
                    variableFiles.add(destFile);
                }

                JSONObject detailObj=new JSONObject();//details列表元素
//                detailObj.put("from",fromEntity);
                detailObj.put("fromFile",srcFileIndex);
                detailObj.put("fromName",singleCollect.getEntityById(fromEntity).getQualifiedName());
                detailObj.put("from",JAEntityList.indexOf(fromEntity));
//                detailObj.put("to",toEntity);
                detailObj.put("toFile",destFileIndex);
                detailObj.put("toName",singleCollect.getEntityById(toEntity).getQualifiedName());
                detailObj.put("to",JAEntityList.indexOf(toEntity));
                String cellkind=relation.getKind();
                if (cellkind.equals("Inherit"))cellkind="Extend";//在ja中叫做extend
                detailObj.put("type",cellkind);
                //位置信息
                JSONObject locObj = new JSONObject();
                locObj.put("line", relation.getLocation().getStartLine());
                locObj.put("row", relation.getLocation().getStartColumn());
                detailObj.put("location", locObj);
                allCells.put(detailObj);
            }
        }
        //只收集JA所要的类型的依赖，因为依赖本身没有id属性可以直接在输出端修改
        List<String> JACellTypes=List.of("Call","Return","UseVar","Contain","Create","Parameter","Import","Inherit","Implement");//Typed
        for (int i=0;i<allCells.length();i++){
            JSONObject jsonObject=allCells.getJSONObject(i);
            String cellkind=jsonObject.getString("type");
//            if(!JACellTypes.contains(cellkind))continue;
//            if(cellkind.equals("Parameter")){
            //查找Parameter、usevar、依赖A的to的id出现在其他Typed的依赖B的from属性，
            //有出现则把A的to改为B的to
            for (int j=0;j<allCells.length();j++){
                if(allCells.getJSONObject(j).getString("type").equals("Typed")){
                    if(jsonObject.getInt("to")==allCells.getJSONObject(j).getInt("from")){
                        jsonObject.put("toName",allCells.getJSONObject(j).getString("toName"));
                        jsonObject.put("to",allCells.getJSONObject(j).getInt("to"));
                    }
                }
            }
//            }
            //判断cell对象是否已经被创建
            Boolean findCell=false;
            JSONArray cells = obj.optJSONArray("cells");
            JSONObject cell=new JSONObject();
            //找到cell。有bug从0->0的cell有两个。因为有一个object的时候cells属性还是object不是array
            int srcFileIndex=jsonObject.getInt("fromFile");
            int destFileIndex=jsonObject.getInt("toFile");
            if(cells.length()>0){
                for (int j=0;j<cells.length();j++){
                    if(cells.getJSONObject(j).getInt("src")==srcFileIndex && cells.getJSONObject(j).getInt("dest")==destFileIndex){
                        cell=cells.getJSONObject(j);
                        findCell=true;
                        break;
                    }
                }
            }
            JSONObject detailObj = new JSONObject();
            detailObj.put("fromName",jsonObject.getString("fromName"));
            detailObj.put("from",jsonObject.getInt("from"));
            detailObj.put("toName",jsonObject.getString("toName"));
            detailObj.put("to",jsonObject.getInt("to"));
            detailObj.put("type",jsonObject.getString("type"));
            detailObj.put("location", jsonObject.getJSONObject("location"));
            if(findCell){
                //cell中已有src,dest,values,details,
                JSONObject values=cell.getJSONObject("values");
                //判断是否已有依赖类型，找不到时默认值0，+1，在values对应依赖类型增加
                //对文件依赖values里增加种类个数
                values.put(cellkind,values.optInt(cellkind,0)+1);
                //json.accumulate是对json的子属性数组追加元素的函数。
                //对文件依赖details里添加实体依赖
                cell.getJSONArray("details").put(detailObj);
            }else{
                //创建依赖json对象src,dest,values,details,
                cell.put("src",srcFileIndex);
                cell.put("dest",destFileIndex);
                JSONObject values=new JSONObject();
                //在values对应依赖类型增加
                values.put(cellkind,1);
                cell.put("values",values);
                cell.put("details",new JSONArray());
                cell.getJSONArray("details").put(detailObj);
                //对cells增加cell个体。
                obj.accumulate("cells",cell);
            }
        }

        //JA的varibles只存file
        obj.put("variables",variableFiles);
        //indices对应原来enre的variables
        obj.put("indices",subObjVariable);
        obj.put("nodeNum:",variableFiles.size());//统计文件个数
        obj.put("indexNum",subObjVariable.size());//统计实体个数，
        // enre分析结果的实体个数远大于ja的分析结果，分析的文件个数也更多
        // 实体更多可能的原因有：enre对实体的种类分析更多，包括对包实体，
        //文件更多的原因：
        obj.put("edgeNum:", obj.optJSONArray("cells").length());//新增，统计依赖个数

        return obj.toString();
    }
    public static String processRawTypeJA (String rawType,String type){
        //只关心var实体的rawType，结果进行转换，对应ja中的的返回类型
        if (rawType == null){
            return null;
        }
        if(type.equals("Var")){
            if (rawType.contains(".")){
                //去掉包路径，以及列表只取最外层。
                if(rawType.contains("<")&&rawType.contains(">")){
                    //substring包括起始字符，不包括结束索引所在字符
                    String subStr=rawType.substring(0,rawType.indexOf("<"));
                    String[] t=subStr.split("\\.");
                    rawType=t[t.length-1];
                }else {
                    String[] temp = rawType.split("\\.");
                    rawType = temp[temp.length - 1];
                }

            }
        }else {
            if (rawType.contains("java.")){
                //list<>,map<>,collection<>,set<>,hashmap<>等，只看最外层<>
                if(rawType.contains("<")&&rawType.contains(">")){
                    //substring包括起始字符，不包括结束索引所在字符
                    String subStr=rawType.substring(0,rawType.indexOf("<"));
                    String[] t=subStr.split("\\.");
                    rawType=t[t.length-1];
                }else {
                    String[] temp = rawType.split("\\.");
                    rawType = temp[temp.length - 1];
                }

            }
        }
        return rawType;
    }

}
