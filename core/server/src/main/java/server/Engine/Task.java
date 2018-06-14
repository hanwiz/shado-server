package server.Engine;
import java.util.*;
import server.Input.loadparam;

/***************************************************************************
 *
 * 	FILE: 			Task.java
 *
 * 	AUTHOR:			ROCKY LI
 *
 * 	LATEST EDIT:	2017/5/24
 *
 * 	VER: 			1.1
 *
 * 	Purpose: 		generate task objects.
 *
 **************************************************************************/

public class Task implements Comparable<Task> {

	//Task specific variables.
	private int Type;
	private int Phase;
	private int shiftPeriod;
	private int Priority;
	private double prevTime;
	private double arrTime;
	private double serTime;
	private double expTime;
	private double elapsedTime;
	private double waitTime;
	private double beginTime;
	private double endTime;
	private int[] opNums;
	public loadparam vars;
	private String name;
	private int vehicleID;
	private boolean expired;
	private boolean fail; // Indicates fail but proceed
	private double lvl_SOME = 0.7;
	private double lvl_FULL = 0.3;
	private double lvl_None = 1.0;

	// This adds functionalities of the RemoteOper

	private boolean isLinked;

	// This adds the ability for task to track queue retroactively

	private int queued;

	// Mutators
	public boolean checkexpired() { return expired; }

	public boolean getFail(){return this.fail;}

	public void setFail(){this.fail = true;}

	public void setArrTime(double time) { arrTime = time;}

	public void setexpired() {
		expired = true;
	}

	public void setELStime (double time){
		elapsedTime = time;
	}

	public void setWaitTime(double time){waitTime = time; }

	public void setID(int id){
		vehicleID = id;
	}

	public void setQueue(int q){
		queued = q-1;
	}

	public void setEndTime(double time){
		endTime = time;
	}

	public void setBeginTime(double time){
		beginTime = time;
	}

	/****************************************************************************
	 *
	 *	Shado Object:	Task
	 *
	 *	Purpose:		Generate a new task on completion of old task. And return
	 *					it's vars.
	 *
	 ****************************************************************************/

	public Task(int type, double PrevTime, loadparam Param, boolean fromPrev) {

		Type = type;
		vars = Param;
		prevTime = PrevTime;
        this.fail = false;

		if (vars.arrPms[type][0] != 0) {
			Phase = getPhase(PrevTime, vars.numHours);
			shiftPeriod = getShiftTime(PrevTime,vars.numHours);
		} else {
			Phase = getPhase(31, vars.numHours);
            shiftPeriod = getShiftTime(PrevTime,vars.numHours);
		}
		if(vars.numPhases == 1)
		    Phase = 0;

		Priority = Param.taskPrty[Type][Phase];
		if (fromPrev == true) {
			arrTime = genArrTime(PrevTime);
		} else {
			arrTime = PrevTime;
		}
		//SCHEN 12/10/17 Fleet Autonomy, Team Coord and Exogenous factor added
		int teamCoordParam = vars.teamCoordAff[Type];
		serTime = genSerTime();
		if(teamCoordParam == 1)
			changeServTime(lvl_SOME);
		else if(teamCoordParam == 2)
			changeServTime(lvl_FULL);
//		applyExogenousFactor();

		//shift schedule 1% fatigue increase serve time
		changeServTime(1 + 0.01 * (shiftPeriod+1));

		expTime = genExpTime();

		beginTime = arrTime;
		opNums = vars.opNums[Type];
		name = vars.taskNames[Type];
//		isLinked = vars.linked[Type] == 1;
		elapsedTime = 0;
		waitTime = 0;
		expired = false;
	}

	/****************************************************************************
	 *
	 *	Method:			compareTo
	 *
	 *	Purpose:		Compare two task based on their priority
	 *
	 ****************************************************************************/

	@Override
	public int compareTo(Task other){
		if (this.Priority != other.Priority){
			return other.Priority - this.Priority;
		} else {
			if (this.arrTime - other.arrTime > 0){
				return 1;
			} else {
				return -1;
			}
		}
	}

	// The following are inspector functions.

	public int getvehicle() {return this.vehicleID;}

	public String getName() {return this.name;}

	public int getQueued() {return this.queued;}

	public boolean linked() {return this.isLinked;}

	public int getType() {return this.Type;}

	public int getPriority(){return this.Priority;}

	public double getPrevTime(){return this.prevTime;}

	public double getArrTime(){return this.arrTime;}

	public double getSerTime(){return this.serTime;}

	public double getEndTime(){return this.endTime;}

	public double getExpTime() {return this.expTime;}

	public double getELSTime() {return this.elapsedTime;}

	public double getBeginTime() {return this.beginTime;}

	public int[] getOpNums() {return this.opNums;}


	/****************************************************************************
	 *
	 *	Method:			GetPhase
	 *
	 *	Purpose:		Return the Phase
	 *
	 ****************************************************************************/


	public int getPhase(double time, double hours){

		if (time<30){
			return 0;
		} else if (time >= 30 && time < hours * 60 - 30) {
			return 1;
		} else return 1;

	}

    /****************************************************************************
     *
     *	Method:			GetPhase
     *
     *	Purpose:		Return the Phase
     *
     ****************************************************************************/


    public int getShiftTime(double time, double hours){
//        System.out.println("at shift period: "+(int)time/60);
        return (int)time/60;
    }


	/****************************************************************************
	 *
	 *	Method:			Exponential
	 *
	 *	Purpose:		Return an exponential distributed random number with input
	 *					vars lambda.
	 *
	 ****************************************************************************/

	private double Exponential(double lambda){

		if (lambda == 0){
			return Double.POSITIVE_INFINITY;
		}
		double result = Math.log(1- Math.random())/(-lambda);

		return result;
	}

	/****************************************************************************
	 *
	 *	Method:			Lognormal
	 *
	 *	Purpose:		Return a lognormal distributed random number with input
	 *					mean and standard deviation.
	 *
	 ****************************************************************************/

	private double Lognormal(double mean, double stddev){

		Random rng = new Random();
		double normal = rng.nextGaussian();
		double l = Math.exp(mean + stddev * normal);

		return l;
	}

	/****************************************************************************
	 *
	 *	Method:			Uniform
	 *
	 *	Purpose:		Return a uniform distribution with input minimum and maximum
	 *
	 ****************************************************************************/

	private double Uniform(double min, double max){

		return min + (max-min)*Math.random();

	}

	/****************************************************************************
	 *
	 *	Method:			genTime
	 *
	 *	Purpose:		Generate a new time with the specified type and vars.
	 *
	 ****************************************************************************/

	private double GenTime (char type, double start, double end){
		switch (type){
			case 'E':
				return Exponential(start);
			case 'L':
				return Lognormal(start, end);
			case 'U':
				return Uniform(start, end);
			case 'T':
				return Triangular(type);
			default:
				throw new IllegalArgumentException("Wrong Letter");
		}
	}

	private double Triangular(char type){
		//DO STUFF
		return 0.0;
	}

	/****************************************************************************
	 *
	 *	Method:			genArrTime
	 *
	 *	Purpose:		Generate a new exponentially distributed arrival time by phase.
	 *
	 ****************************************************************************/

	private double genArrTime(double PrevTime){
		//SCHEN 12/16/17 Add fleet autonomy function by decreasing the arrival rate
		double arrivalRate = changeArrivalRate(getFleetAutonomy());
		double TimeTaken = Exponential(arrivalRate);

		if (TimeTaken == Double.POSITIVE_INFINITY){
			return Double.POSITIVE_INFINITY;
		}

		double newArrTime = TimeTaken + PrevTime;

		if (vars.affByTraff[Type][Phase] == 1 && loadparam.TRAFFIC_ON){

			double budget = TimeTaken;
			double currTime = prevTime;
			int currHour = (int) currTime/60;
			double traffLevel = vars.traffic[currHour];
			double TimeToAdj = (currHour+1)*60 - currTime;
			double adjTime = TimeToAdj * traffLevel;

			while (budget > adjTime){

				budget -= adjTime;
				currTime += TimeToAdj;
				currHour ++;

				if (currHour >= vars.traffic.length){
					return Double.POSITIVE_INFINITY;
				}

				traffLevel = vars.traffic[currHour];
				TimeToAdj = (currHour + 1)*60 - currTime;
				adjTime = TimeToAdj * traffLevel;

			}

			newArrTime = currTime + budget/traffLevel;
		}

		return newArrTime;
	}

	/****************************************************************************
	 *
	 *	Method:			genSerTime
	 *
	 *	Purpose:		Generate a new service time.
	 *
	 ****************************************************************************/

	private double genSerTime(){

		char type = vars.serDists[Type];
		double start = vars.serPms[Type][0];
		double end = vars.serPms[Type][1];
//		System.out.println("genSerTime: Sertime " +start+ " " +end);
		return GenTime(type, start, end);

	}

	/****************************************************************************
	 *
	 *	Method:			genExpTime
	 *
	 *	Purpose:		Generate a new Expiration time.
	 *
	 ****************************************************************************/

	private double genExpTime(){

		double param;
		double expiration = 0;
		int hour = (int) arrTime/60;
		if (hour >= vars.traffic.length){
			return arrTime;
		} else if (vars.traffic[hour] == 2){
			param = vars.expPmsHi[Type][Phase];
		} else {
			param = vars.expPmsLo[Type][Phase];
		}
		expiration = GenTime(vars.expDists[Type], param, 0);
		return arrTime + 2*serTime + expiration;

	}

	/****************************************************************************
	 *
	 *	Method:			getFleetAutonomy
	 *
	 *	Purpose:		Fetch fleet autonomy level
	 *					0: default
	 *					1: Some 70% of arrival rate
	 *					2: Full 30% of arrival rate
	 *
	 ****************************************************************************/

	private double getFleetAutonomy(){
		//SCHEN 12/10/17: Add Fleet autonomy -> adjust arrival rate
		double autoLevel = 1;

		if(vars.autolvl == 1) autoLevel = lvl_SOME;
		if(vars.autolvl == 2) autoLevel = lvl_FULL;

		return  autoLevel;
	}

//	private  double getTeamComm(){
//		double teamComm = 1;
//		if(vars.teamCoordAff == 1) teamComm = 0.7;
//		if(vars.autolvl == 2) teamComm = 0.3;
//
//		return  teamComm;
//	}
	/****************************************************************************
	 *
	 *	Method:			changeServTime, changeArrivalRate
	 *
	 *	Purpose:		change Service time and arrival rate by multiply by a number
	 *
	 ****************************************************************************/
	public double changeServTime(double num){
		serTime *= num;
		return serTime;
	}

	private double changeArrivalRate(double num){
		return vars.arrPms[Type][Phase]*num;
	}

	private void applyExogenousFactor(){

		if(vars.hasExogenous[0] == 1){
			int numExo = vars.hasExogenous[1];
			for(int i = 0; i < numExo; i++){
				if(vars.exTypes[i].equals("long_serv")){
					changeServTime(1.1);
				}
				if (vars.exTypes[i].equals("add_task")) {
					//TODO: additional Task function
				}
				if(vars.exTypes[i].equals("inc_arrival")){
				    //Reserved for future usage
					changeArrivalRate(1.1);
				}
			}
		}
	}	//END applyExogenousFactor()

	private void applyTeamCoord(char lvlTeamCoord){
		if(lvlTeamCoord =='S') changeArrivalRate(0.7);
        if(lvlTeamCoord =='F') changeArrivalRate(0.3);
	}

}


