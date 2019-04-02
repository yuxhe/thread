package com.action.cxwms.common.util;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.action.cxwms.common.mybatis.entity.BasContractbaseinfo;
import com.action.cxwms.common.mybatis.mapper.BasContractbaseinfoMapper;


/**
 * @author yuxh
 * @Description  多线程异步分解 处理合同状态数据
 * @revise
 * @time 2018年7月3日 下午5:50:07
 * @version 1.0
 * @copyright Copyright @2017, Co., Ltd. All right.
 */

@Component
public class ScheduledTasks {
	private static final Logger logger = LogManager.getLogger(ScheduledTasks.class);
	@Autowired
	private   BasContractbaseinfoMapper  basContractbaseinfoMapper;
    
    @Scheduled(initialDelay=20,fixedRate = 180 * 60 * 1000) //20 分钟刷新缓存  20 * 60 * 1000
    public void  loadUpdateContract() {
       @SuppressWarnings("unchecked")
       BasContractbaseinfo  query=new BasContractbaseinfo();
       List<BasContractbaseinfo>  list=basContractbaseinfoMapper.findList(query) ;
       
       int count = 1000;       //暂时按一千条划分
       int listSize = list.size();
       int RunSize = (listSize / count)+1;   //20000/1000

       ExecutorService executor= Executors.newFixedThreadPool(RunSize);
       for (int i = 0; i < RunSize; i++) {
    	   List<BasContractbaseinfo> newList = null ;
           if((i+1)==RunSize){
               int startIndex = (i*count);
               int endIndex = list.size();
               newList =list.subList(startIndex,endIndex);
           }else{
               int startIndex = i*count;
               int endIndex = (i+1)*count;
               newList =list.subList(startIndex,endIndex);
           }
           ExecupteContract execupteContract = new ExecupteContract(newList,"Thread"+i);
           executor.execute(execupteContract);
       }
       executor.shutdown();
       System.out.println("合同状态 --- 开始建立");
       logger.info("合同状态 --- 开始建立");
    }
    
    //----------------------------------------------------
    class ExecupteContract implements Runnable{ //填充数据哈
        private List <BasContractbaseinfo> list;
        private ThreadLocal<Integer> threadLocal = null;//记录当前循环的次数
        private String  name;//线程名称
        public ExecupteContract (List <BasContractbaseinfo> list,String name){
            this.list  = list ;
            this.name=name;//线程名称
        }

        @Override
        public void run() {
            if(null!=list){
            	Iterator<BasContractbaseinfo> iter=list.iterator() ;
            	if (threadLocal==null) {
                {
                	threadLocal = new ThreadLocal<Integer>() ;
                    threadLocal.set(0);
                 }
            	}
                try {
                Date  now=new Date();
                while (iter.hasNext() &&  threadLocal.get() <1000) {//按1000个分隔数据
                   BasContractbaseinfo  basContractbaseinfo = iter.next();//             
             	   threadLocal.set(threadLocal.get()+1);
             	   //---------------------------------- //批量处理数据的状态
             	   //合同状态  8已生效、9已作废、1未生效、4未提交   7已过期
             	   if (basContractbaseinfo.getContractstatus()==9) {//排开已作废的
             		   continue;
             	   }
             	  if (basContractbaseinfo.getDuedate()!=null) {
             		  basContractbaseinfo.setDuedate(DateUtils.parseDate(DateUtils.formatDate(basContractbaseinfo.getDuedate(), "yyyy-MM-dd")+" 23:59:59", "yyyy-MM-dd HH:mm:ss"));
             	  }
             	  if (basContractbaseinfo.getEffectivedate() != null && basContractbaseinfo.getDuedate() != null) {
          			if (basContractbaseinfo.getEffectivedate().getTime() > now.getTime()) {// 合同未生效
          				basContractbaseinfo.setContractstatus(1);
          			} else if (now.getTime() <= basContractbaseinfo.getDuedate().getTime()) {// 已生效
          				basContractbaseinfo.setContractstatus(8);
          			} else if (now.getTime() > basContractbaseinfo.getDuedate().getTime()) {// 已过期
          				basContractbaseinfo.setContractstatus(7);
          			}
          		  }
             	  //----------------------------------
         		}
                if  (this.list!=null && !this.list.isEmpty()) {//批量修改合同 状态数据
                	basContractbaseinfoMapper.batchUpdate(this.list) ;
                }
                logger.info(this.name+"线程处理结束,共处理数据"+ threadLocal.get() +"条" );
                }catch (Exception e) {
                	System.out.println("--------------------------");
        			e.printStackTrace();
        			logger.info(e.getMessage());
        		}               
            }
        }
    }
    //----------------------------------------------------
 
}