package com.nice.coday;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.util.Pair;

public class ElectricityConsumptionCalculatorImpl implements ElectricityConsumptionCalculator {
    
    static double percentPerKm(int Mileage){
        return (double) 100.0/Mileage;
    }

    static double kmPerPercent(int Mileage){
        return (double) Mileage/100.0;
    }

    static double unitsConsumePerPercent(double units){
        return (double) units/100.0;
    }

    static int findUpperBound(ArrayList<Double> list, Double temp) {
        int low = 0, high = list.size() - 1;
        int result = -1;  

        while (low <= high) {
            int mid = (low + high) / 2;

            if (list.get(mid) <= temp) {
                result = mid;
                low = mid + 1;  
            } else {
                high = mid - 1;
            }
        }
        return result; 
    }

    @Override
    public ConsumptionResult calculateElectricityAndTimeConsumption(ResourceInfo resourceInfo) throws IOException {

        ArrayList<Double> chargingStation = new ArrayList<Double>(); 
        ArrayList<Double> entryExit = new ArrayList<Double>();  
        ArrayList<ArrayList<Double>> vehicleMeasurement = new ArrayList<ArrayList<Double>>();  

        // Charging Station arraylist
        String line =  null;
        BufferedReader br = new BufferedReader(new FileReader(resourceInfo.chargingStationInfoPath.toFile()));
        br.readLine();
        while((line=br.readLine())!=null){
            String str[] = line.split(",");

            String station = str[0].trim();
            String numberPart = station.substring(1).trim();

            int index = Integer.parseInt(numberPart) - 1;
            Double distance = Double.parseDouble(str[1].trim());
            chargingStation.add(index, distance);
        }
        br.close();

        // entry Exit point arraylist
        br = new BufferedReader(new FileReader(resourceInfo.entryExitPointInfoPath.toFile()));
        br.readLine();
        while ((line = br.readLine()) != null) {
            String str[] = line.split(",");

            String entryExitpoint = str[0].trim();
            String numberPart = entryExitpoint.substring(1).trim();

            int index = Integer.parseInt(numberPart) - 1;
            Double distanceFromStart = Double.parseDouble(str[1].trim());
            entryExit.add(index, distanceFromStart);
        }
        br.close();

        // Vehicle Info array of array
        br = new BufferedReader(new FileReader(resourceInfo.vehicleTypeInfoPath.toFile()));
        br.readLine();
        while ((line = br.readLine()) != null) {
            String str[] = line.split(",");

            String vehicleType = str[0];
            String numberPart = vehicleType.substring(1);
            int index = Integer.parseInt(numberPart) - 1;
            int fullyChargeUnits = Integer.parseInt(str[1]);
            int mileage = Integer.parseInt(str[2]);

            double percentPerKm = percentPerKm(mileage);
            double kmPerPercent = kmPerPercent(mileage);
            double unitsConsumePerPercent = unitsConsumePerPercent(fullyChargeUnits);
            
            ArrayList<Double> temp = new ArrayList<Double>(3);  
            temp.add(0,percentPerKm);
            temp.add(1,kmPerPercent);
            temp.add(2,unitsConsumePerPercent);

            vehicleMeasurement.add(index,temp);
        }
        br.close();

        // Time to charge
        Map<Pair<Integer, Integer>, Integer> timeToChargeVehicleInfo = new HashMap<>();
        br = new BufferedReader(new FileReader(resourceInfo.timeToChargeVehicleInfoPath.toFile()));
        br.readLine();

        while ((line = br.readLine()) != null) {
            String[] str = line.split(","); 
           
            int r = Integer.parseInt(str[0].substring(1)) - 1;
            int w = Integer.parseInt(str[1].substring(1)) - 1;
            int y = Integer.parseInt(str[2]);
            Pair<Integer, Integer> vehicleStationPair = new Pair<>(r, w);

            timeToChargeVehicleInfo.put(vehicleStationPair,y);
        }
        br.close();
        
        HashMap<String,Long> TotalChargingStationTime = new HashMap<>();
        List<ConsumptionDetails> consumptionDetails = new ArrayList<>();
        
        int numberOfVehicles = vehicleMeasurement.size(); 
        for (int i = 0; i < numberOfVehicles; i++) {
            consumptionDetails.add(new ConsumptionDetails("V" + (i + 1), 0.0, 0L, 0L));
        }

        br = new BufferedReader(new FileReader(resourceInfo.tripDetailsPath.toFile()));
        br.readLine();

        while((line = br.readLine()) != null) {
            String[] str = line.split(","); 

            int vehicleIndex = Integer.parseInt(str[1].substring(1)) - 1;
            int startInd = Integer.parseInt(str[3].substring(1)) - 1;
            int exitInd = Integer.parseInt(str[4].substring(1)) - 1;
            Double remPercent = Double.parseDouble(str[2]);

            Double currDist = entryExit.get(startInd);
            Double exitDist = entryExit.get(exitInd);
            int prevChargeStation = -1; 
            boolean flag = false;

            double batteryConsumptionRate = vehicleMeasurement.get(vehicleIndex).get(0);
            double kmPerPercent = vehicleMeasurement.get(vehicleIndex).get(1);
            double unitsPerPercent = vehicleMeasurement.get(vehicleIndex).get(2);

            batteryConsumptionRate = Math.round(batteryConsumptionRate * 100) / 100.0; 
            kmPerPercent = Math.round(kmPerPercent * 100)/100.0;
            unitsPerPercent = Math.round(unitsPerPercent * 100 )/100.0; 

            while ( currDist < exitDist ) {
                Double canGoto = remPercent * kmPerPercent;
                Double temp = currDist + canGoto;
                // reached 
                if( temp >= exitDist )  break;
                
                Double prevDist = currDist;
                int chargeStationIndex = findUpperBound(chargingStation,temp);
                
                if( prevChargeStation == chargeStationIndex || chargeStationIndex == -1){
                    flag = true;    break; 
                }

                String chargeStationKey = "C" + (chargeStationIndex + 1);
                currDist = chargingStation.get(chargeStationIndex);
                Double tempDistanceTravelled = currDist - prevDist;
                // negative case
                if( tempDistanceTravelled <= 0 ){
                    flag = true;    break;
                }

                Double rate1 = vehicleMeasurement.get(vehicleIndex).get(0);
                Double percentUsed = tempDistanceTravelled * rate1;
                remPercent -= percentUsed;
                Double toCharge = 100 - remPercent;
                remPercent = 100.0;
                
                Double unitsConsumed = toCharge * unitsPerPercent;
                Integer tp = timeToChargeVehicleInfo.get(new Pair<Integer, Integer>(vehicleIndex,chargeStationIndex));
                Double timeConsumed = unitsConsumed * tp.doubleValue();
                
                // update map
                if (TotalChargingStationTime.containsKey(chargeStationKey)) {
                    Long currentTime = TotalChargingStationTime.get(chargeStationKey);
                    TotalChargingStationTime.put(chargeStationKey, currentTime + timeConsumed.longValue() );
                } else {
                    TotalChargingStationTime.put(chargeStationKey, timeConsumed.longValue());
                }

                // Update arraylist
                Long timeTemp = consumptionDetails.get(vehicleIndex).getTotalTimeRequired();
                consumptionDetails.get(vehicleIndex).setTotalTimeRequired(timeTemp + timeConsumed.longValue());
                
                Double unitTemp = consumptionDetails.get(vehicleIndex).getTotalUnitConsumed();
                consumptionDetails.get(vehicleIndex).setTotalUnitConsumed(unitTemp + unitsConsumed);

                prevChargeStation = chargeStationIndex;
            }

            if( flag ) continue;

            Long cnt = consumptionDetails.get(vehicleIndex).getNumberOfTripsFinished();
            consumptionDetails.get(vehicleIndex).setNumberOfTripsFinished(cnt + 1);
        }   
        br.close();

        ConsumptionResult ans = new ConsumptionResult();
        ans.setTotalChargingStationTime(TotalChargingStationTime);
        ans.setConsumptionDetails(consumptionDetails);

        return ans;
    }
}