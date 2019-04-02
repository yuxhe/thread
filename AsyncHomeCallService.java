package com.action.bdapp.cschoolstu.modules.homepage.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.action.bandou.common.util.JsonUtil;
import com.action.bdapp.cschoolstu.common.mybatis.entity.BasGuarsturelation;
import com.action.bdapp.cschoolstu.common.mybatis.entity.EduClasshomework;
import com.action.bdapp.cschoolstu.common.mybatis.entity.EduClassrecommend;
import com.action.bdapp.cschoolstu.common.mybatis.entity.EduSchoolcalendar;
import com.action.bdapp.cschoolstu.common.mybatis.entity.EduStudentrecipe;
import com.action.bdapp.cschoolstu.common.mybatis.entity.InfSchoolnewsnotice;
import com.action.bdapp.cschoolstu.modules.homepage.model.ModelClsStu;
import com.action.bdapp.cschoolstu.modules.homepage.model.ModelHome;
import com.action.bdapp.cschoolstu.modules.homepage.service.HomeService;
import com.action.bdapp.cschoolstu.modules.school.model.CalendarmWeek;

/**
 * @author yuxh
 * @Description  
 * @revise
 * @time     2017年11月7日 下午2:24:27
 * @version 1.0
 * @copyright Copyright @2017, Co., Ltd. All right.
 */
@Service
public class AsyncHomeCallService {
   @Resource
   private   AsyncHomeService  asyncHomeService ;
   @Resource
   private   HomeService  homeService ;
   
   public  List<ModelHome>  getHome(String appkey,String guardianaccno) throws InterruptedException, ExecutionException {
	   //获取该人拥有的班级及学生accno
	   List<BasGuarsturelation> liststu=homeService.getClassStudent(appkey, guardianaccno) ;
	   //处理班级 及学生
	   List<ModelClsStu>  modelClsStuL=new ArrayList<ModelClsStu>();
	   //-------------------------处理班级
	   List<Long>  classid=new ArrayList<Long>();
	   for  (BasGuarsturelation tmp:liststu)   {
		     if (!classid.contains(tmp.getClassid()))  {
		    	 classid.add(tmp.getClassid());
		     }
	   }
	   //-------------------------
	   for  (BasGuarsturelation tmp:liststu) {
		     int i_index=-1;
		     for  (ModelClsStu tmpM:modelClsStuL) {
		    	   if  (tmp.getClassid().longValue()==tmpM.getClassid().longValue()) {
		    		   i_index=i_index+1;
		    		   break;
		    	   }
		     }
		     if (i_index >=0) {//找到
		    	 ModelClsStu   modelClsStu=modelClsStuL.get(i_index);
		    	 modelClsStu.setStudentaccno(modelClsStu.getStudentaccno() + ","+tmp.getStudentaccno());
		     }else {//增加数据
		    	 ModelClsStu   modelClsStu=new  ModelClsStu();
		    	 modelClsStu.setAppkey(appkey);
		    	 modelClsStu.setClassid(tmp.getClassid());
		    	 modelClsStu.setClassids(classid);
		    	 modelClsStu.setStudentaccno(tmp.getStudentaccno());
		    	 modelClsStu.setClassname(tmp.getClassname());//班级名称
		    	 modelClsStuL.add(modelClsStu) ;
		     }
		     
	   }
	   
	   /*
	   if  (modelClsStuL.isEmpty()) {//无数据时特殊处理
		    ModelClsStu   modelClsStu=new  ModelClsStu();
	    	modelClsStu.setAppkey(appkey);
	    	modelClsStu.setClassid(0L);
	    	modelClsStu.setStudentaccno("无");
	    	modelClsStu.setClassname("");//班级名称
	    	 
	   }
	   */
	   //---------------------------------------------------------------------------
	   //1、作业循环调用多次  
	   //-------------------------
	   int ii_i=4 - modelClsStuL.size() ;
	   List<Long>  classids=new ArrayList<Long>();
	   classids.add(0L);
	   for  (int i=0;i<ii_i ;i++)  {
		    ModelClsStu   modelClsStu=new  ModelClsStu();
	    	modelClsStu.setAppkey(appkey);
	    	modelClsStu.setClassid(0L);
	    	modelClsStu.setClassids(classids);
	    	modelClsStu.setStudentaccno("无");
	    	modelClsStu.setClassname("");//班级名称
	    	modelClsStuL.add(modelClsStu);
	   }
	   //-------------------------
		Future<List<EduClasshomework>> homework1 = asyncHomeService.getHomework(modelClsStuL.get(0)) ;
		Future<List<EduClasshomework>> homework2 = asyncHomeService.getHomework(modelClsStuL.get(1)) ;
        Future<List<EduClasshomework>> homework3 = asyncHomeService.getHomework(modelClsStuL.get(2)) ;
        Future<List<EduClasshomework>> homework4 = asyncHomeService.getHomework(modelClsStuL.get(3)) ;

	   //2、处理资讯公告  各班1 条
       //System.out.println(JsonUtil.toJson(modelClsStuL.get(0))) ;
	   Future<List<InfSchoolnewsnotice>> infL=asyncHomeService.getHomeSchoolnewsnotice(modelClsStuL.get(0),modelClsStuL);

	   //3、处理食谱  一天的数据
	   EduStudentrecipe  query=new EduStudentrecipe();
	   query.setAppkey(appkey);
	   Future<List<EduStudentrecipe>> studentrecipeL=asyncHomeService.getHomeStudentrecipe(query);

	   //4、处理精选  各班一条
	   Future<List<EduClassrecommend>> classrecommendL=asyncHomeService.getHomeClassrecommend(modelClsStuL.get(0),modelClsStuL);

	   //5、处理校历 
	   EduSchoolcalendar  queryc=new EduSchoolcalendar();
	   queryc.setAppkey(appkey);
	   Future<Map<String,Object>>  MapcalendarmWeek=asyncHomeService.getHomeCalendarmWeek(queryc);

       while(true) {
           if(( homework1.isDone()) && (homework2.isDone()) && (homework3.isDone()) && (homework4.isDone())
        	  &&  (infL.isDone())  &&  (studentrecipeL.isDone()) 
        	  &&  (classrecommendL.isDone())  && (MapcalendarmWeek.isDone())  )
           break;
           Thread.sleep(100);
       }
       //-------------------------------比较排序 除开 校历之外  infL  studentrecipeL  classrecommendL
       //排序---------------------------
       //每个里面获取一条 比较  ,作业
       //object :  hometype, modifydate, value:list或 object
       List<ModelHome>  listo=new ArrayList<ModelHome>();
       ModelHome  modelHome=null;
       if  (homework1!=null && homework1.get()!=null &&  !homework1.get().isEmpty()) {
    	    modelHome=new ModelHome();
    	    modelHome.setHometype(1);
    	    modelHome.setModifydate(homework1.get().get(0).getModifydate());
    	    modelHome.setObject(homework1.get());
    	    listo.add(modelHome);
       }
       
       if  (homework2!=null && homework2.get()!=null &&  !homework2.get().isEmpty()) {
	   	    modelHome=new ModelHome();
	   	    modelHome.setHometype(1);
	   	    modelHome.setModifydate(homework2.get().get(0).getModifydate());
	   	    modelHome.setObject(homework2.get());
	   	    listo.add(modelHome);
       }
       if  (homework3!=null && homework3.get()!=null &&  !homework3.get().isEmpty()) {
	   	    modelHome=new ModelHome();
	   	    modelHome.setHometype(1);
	   	    modelHome.setModifydate(homework3.get().get(0).getModifydate());
	   	    modelHome.setObject(homework3.get());
	   	    listo.add(modelHome);
       }
       if  (homework4!=null && homework4.get()!=null &&  !homework4.get().isEmpty()) {
	   	    modelHome=new ModelHome();
	   	    modelHome.setHometype(1);
	   	    modelHome.setModifydate(homework4.get().get(0).getModifydate());
	   	    modelHome.setObject(homework4.get());
	   	    listo.add(modelHome);
       }
       
       if  (infL!=null && infL.get()!=null &&  !infL.get().isEmpty()) {
    	    for  (InfSchoolnewsnotice tmp:infL.get())  {//2,3,4,5
    	    	 modelHome=new ModelHome();
    	    	 if ("schoolnews".equals(tmp.getNewkind())) {//校园新闻schoolnews  校园公告schoolnotice   班级通知classnotice   班级资讯classnews
    	    		 modelHome.setHometype(3);//
    	    	 }
    	    	 if ("schoolnotice".equals(tmp.getNewkind())) {//校园新闻schoolnews  校园公告schoolnotice   班级通知classnotice   班级资讯classnews
    	    		 modelHome.setHometype(2);//
    	    	 }
    	    	 if ("classnotice".equals(tmp.getNewkind())) {//校园新闻schoolnews  校园公告schoolnotice   班级通知classnotice   班级资讯classnews
    	    		 modelHome.setHometype(4);//
    	    	 }
    	    	 if ("classnews".equals(tmp.getNewkind())) {//校园新闻schoolnews  校园公告schoolnotice   班级通知classnotice   班级资讯classnews
    	    		 modelHome.setHometype(5);//
    	    	 }
    		   	 modelHome.setModifydate(tmp.getModifydate());
    		   	 //modelHome.setObject(tmp);
    		   	 List<InfSchoolnewsnotice> tmpL=new ArrayList<InfSchoolnewsnotice>();
    		   	 tmpL.add(tmp);
    		     modelHome.setObject(tmpL);
    		   	 listo.add(modelHome);
    	    }
       }
       if  (studentrecipeL!=null && studentrecipeL.get()!=null &&  !studentrecipeL.get().isEmpty()) {
	   	    modelHome=new ModelHome();
	   	    modelHome.setHometype(6);//食谱
	   	    modelHome.setModifydate(studentrecipeL.get().get(0).getModifydate());
	   	    modelHome.setObject(studentrecipeL.get());
	   	    listo.add(modelHome);
       }
       if  (classrecommendL!=null && classrecommendL.get()!=null &&  !classrecommendL.get().isEmpty()) {
    	    for  (EduClassrecommend tmp:classrecommendL.get()) {
		   	    modelHome=new ModelHome();
		   	    modelHome.setHometype(7);//推荐
		   	    modelHome.setModifydate(tmp.getModifydate());
		   	    
		   	    List<EduClassrecommend> tmpL=new  ArrayList<EduClassrecommend>();
		   	    tmpL.add(tmp);
		        modelHome.setObject(tmpL);
		   	    listo.add(modelHome);
    	    }
       }
       //排序规则
       Collections.sort(listo,new Comparator<ModelHome>(){
           public int compare(ModelHome arg0, ModelHome arg1) {
               return arg1.getModifydate().compareTo(arg0.getModifydate());
           }
       });
       
       if  (MapcalendarmWeek!=null &&  MapcalendarmWeek.get()!=null &&  !MapcalendarmWeek.get().isEmpty()) {
    	    modelHome=new ModelHome();
	   	    modelHome.setHometype(8);//校历
	   	    modelHome.setCalendname((String)MapcalendarmWeek.get().get("calendname"));
	   	    modelHome.setTermdate((String)MapcalendarmWeek.get().get("termdate"));
	   	    List<CalendarmWeek> calendarmWeekL=(List<CalendarmWeek>)(MapcalendarmWeek.get().get("list"));
	   	    if  (calendarmWeekL!=null && !calendarmWeekL.isEmpty() &&  calendarmWeekL.get(0)!=null) {
	   	        modelHome.setModifydate(calendarmWeekL.get(0).getWeekend());
	   	    }
	   	    modelHome.setObject(calendarmWeekL);
	   	    listo.add(modelHome);
       }
       //---------------------------------------------------
       return  listo;
   }
}
