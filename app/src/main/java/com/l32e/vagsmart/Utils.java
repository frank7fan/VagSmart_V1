package com.l32e.vagsmart;

import java.util.Arrays;

/**
 * Created by frank.fan on 3/8/2018.
 */

public class Utils {

    //map function take ADC reading from [0:2047] and map to [0:100] base on CalMin and CalMax
    public static int map(int inputValue, int inputMin, int inputMax){
        int extendMin, extendMax;
        //if inputMin means 10%; inputMax means 90%
        if (inputMin!=0 | inputMax!=GraphActivity.RANGE_11B){
            extendMin = inputMin - ((inputMax - inputMin) / 8);
            extendMax = inputMax + ((inputMax - inputMin) / 8);
        }else{
            extendMin = inputMin;
            extendMax = inputMax;
        }
        //mapping from (extendMin,extendMax) to (0, 100)
        if (inputValue < extendMin){
            return 0;}
        else if (inputValue > extendMax){
            return 100;}
        else return (inputValue-extendMin)*100/(extendMax-extendMin);
        //else return inputValue/10;
    }

    public static int displayNumber(String method, int[] inputArray){
        Arrays.sort(inputArray);
        return (inputArray[inputArray.length-1]+inputArray[inputArray.length-2])/2;
    }

    public static void enableCalButtons(Boolean On){
        GraphActivity.calMaxButton.setEnabled(On);
        GraphActivity.calMinButton.setEnabled(On);
        GraphActivity.calClearButton.setEnabled(On);
        if (!On){
            GraphActivity.calMaxButton.setBackgroundResource(R.drawable.button_calbrations);
            GraphActivity.calMinButton.setBackgroundResource(R.drawable.button_calbrations);
        }
    }
}
