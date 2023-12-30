package com.vadrin.rincey360;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExcelDataController {

	@PostMapping("/api/receiveExcelData")
	public List<DataPoint> receiveExcelData(@RequestBody ExcelDataRequest excelDataRequest,
			@RequestParam int numberOfRequiredPoints) {
		List<DataPoint> rowDataList = new ArrayList<>();
		String excelData = excelDataRequest.getData();

		// Split the data into rows
		String[] rows = excelData.split(" ");

		// Add each row to the list
		for (String row : rows) {
			rowDataList.add(new DataPoint(Double.valueOf(row.split("\\t")[0]), (Double.valueOf(row.split("\\t")[1]))));
		}
		
		// sort
		rowDataList = rowDataList.stream().sorted((a, b) -> Double.compare(a.getX(), b.getX()))
				.collect(Collectors.toList());

		int prioIndex = identifyPrioDataPoint(rowDataList);

		double xr = rowDataList.get(prioIndex).getX();
		double yr = rowDataList.get(prioIndex).getY();
		double xl = rowDataList.get(prioIndex-1).getX();
		double yl = rowDataList.get(prioIndex-1).getY();
		double xn = (xr - xl) / 2;
		double yn = (((xn - xl)*yr) + ((xr-xn)*yl)) / ((xr - xn) + (xn - xl));
		
		double xr2 = rowDataList.get(prioIndex+1).getX();
		double yr2 = rowDataList.get(prioIndex+1).getY();
		double xl2 = rowDataList.get(prioIndex).getX();
		double yl2 = rowDataList.get(prioIndex).getY();
		double xn2 = (xr2 - xl2) / 2;
		double yn2 = (((xn2 - xl2)*yr2) + ((xr2-xn2)*yl2)) / ((xr2 - xn2) + (xn2 - xl2));
		
		List<DataPoint> toReturn = new ArrayList<>();
		toReturn.add(new DataPoint(xn, yn));
		toReturn.add(new DataPoint(xn2, yn2));
		return toReturn;
	}

	private int identifyPrioDataPoint(List<DataPoint> rowDataList) {

		// leftslope & rightSlope
		for (int i = 1; i < rowDataList.size(); i++) {
			double m = (rowDataList.get(i).getY() - rowDataList.get(i - 1).getY())
					/ (rowDataList.get(i).getX() - rowDataList.get(i - 1).getX());
			rowDataList.get(i).setLeftSlope(m);
			rowDataList.get(i - 1).setRightSlope(m);
		}

		// slopeDiff
		for (int i = 0; i < rowDataList.size(); i++) {
			rowDataList.get(i)
					.setSlopeDiff(Math.abs(rowDataList.get(i).getRightSlope() - rowDataList.get(i).getLeftSlope()));
		}

		// xDiff & yDiff
		for (int i = 1; i < rowDataList.size() - 1; i++) {
			rowDataList.get(i).setxDiff(Math.abs(rowDataList.get(i).getX() - rowDataList.get(i - 1).getX())
					+ Math.abs(rowDataList.get(i + 1).getX() - rowDataList.get(i).getX()));
			rowDataList.get(i).setyDiff(Math.abs(rowDataList.get(i).getY() - rowDataList.get(i - 1).getY())
					+ Math.abs(rowDataList.get(i + 1).getY() - rowDataList.get(i).getY()));
		}

		// normalize y Diff
		double y_min = Double.MAX_VALUE;
		double y_max = Double.MIN_VALUE;
		for (Double value : rowDataList.stream().map(x -> x.getyDiff()).collect(Collectors.toList())) {
			y_min = Math.min(y_min, value);
			y_max = Math.max(y_max, value);
		}
		for (int i = 0; i < rowDataList.size(); i++) {
			double normalizedValue = (rowDataList.get(i).getyDiff() - y_min) / (y_max - y_min);
			rowDataList.get(i).setyDiffNormalized(normalizedValue);
		}

		// normalize x Diff
		double x_min = Double.MAX_VALUE;
		double x_max = Double.MIN_VALUE;
		for (Double value : rowDataList.stream().map(x -> x.getxDiff()).collect(Collectors.toList())) {
			x_min = Math.min(x_min, value);
			x_max = Math.max(x_max, value);
		}
		for (int i = 0; i < rowDataList.size(); i++) {
			double normalizedValue = (rowDataList.get(i).getxDiff() - x_min) / (x_max - x_min);
			rowDataList.get(i).setxDiffNormalized(normalizedValue);
		}

		// normalize slopeDiff
		double m_min = Double.MAX_VALUE;
		double m_max = Double.MIN_VALUE;
		for (Double value : rowDataList.stream().map(x -> x.getSlopeDiff()).collect(Collectors.toList())) {
			m_min = Math.min(m_min, value);
			m_max = Math.max(m_max, value);
		}
		for (int i = 0; i < rowDataList.size(); i++) {
			double normalizedValue = (rowDataList.get(i).getSlopeDiff() - m_min) / (m_max - m_min);
			rowDataList.get(i).setSlopeDiffNormalized(normalizedValue);
		}

		// priority
		for (int i = 0; i < rowDataList.size(); i++) {
			rowDataList.get(i).setPriority(rowDataList.get(i).getSlopeDiffNormalized()
					+ rowDataList.get(i).getxDiffNormalized() + rowDataList.get(i).getyDiffNormalized());
		}

		double maxSoFar = rowDataList.get(1).getPriority();
		int toReturn = 1;
		for (int i = 1; i < rowDataList.size()-1; i++) {

			if(rowDataList.get(i).getPriority() > maxSoFar) {
				toReturn = i;
				maxSoFar = rowDataList.get(i).getPriority();
			}

		}
		
		return toReturn;
	}

	// Class representing the structure of the JSON request
	static class ExcelDataRequest {
		private String data;

		public String getData() {
			return data;
		}

		public void setData(String data) {
			this.data = data;
		}
	}

	// Class representing the structure of the JSON request
	static class DataPoint {
		private double x;
		private double y;
		private double leftSlope;
		private double rightSlope;
		private double slopeDiff;
		private double xDiff;
		private double yDiff;
		private double slopeDiffNormalized;
		private double xDiffNormalized;
		private double yDiffNormalized;
		private double priority;

		public double getX() {
			return x;
		}

		public void setX(double x) {
			this.x = x;
		}

		public double getY() {
			return y;
		}

		public void setY(double y) {
			this.y = y;
		}

		public double getLeftSlope() {
			return leftSlope;
		}

		public void setLeftSlope(double leftSlope) {
			this.leftSlope = leftSlope;
		}

		public double getRightSlope() {
			return rightSlope;
		}

		public void setRightSlope(double rightSlope) {
			this.rightSlope = rightSlope;
		}

		public double getSlopeDiff() {
			return slopeDiff;
		}

		public void setSlopeDiff(double slopeDiff) {
			this.slopeDiff = slopeDiff;
		}

		public double getxDiff() {
			return xDiff;
		}

		public void setxDiff(double xDiff) {
			this.xDiff = xDiff;
		}

		public double getyDiff() {
			return yDiff;
		}

		public void setyDiff(double yDiff) {
			this.yDiff = yDiff;
		}

		public double getPriority() {
			return priority;
		}

		public void setPriority(double priority) {
			this.priority = priority;
		}

		public DataPoint(double x, double y) {
			super();
			this.x = x;
			this.y = y;
		}

		public double getSlopeDiffNormalized() {
			return slopeDiffNormalized;
		}

		public void setSlopeDiffNormalized(double slopeDiffNormalized) {
			this.slopeDiffNormalized = slopeDiffNormalized;
		}

		public double getxDiffNormalized() {
			return xDiffNormalized;
		}

		public void setxDiffNormalized(double xDiffNormalized) {
			this.xDiffNormalized = xDiffNormalized;
		}

		public double getyDiffNormalized() {
			return yDiffNormalized;
		}

		public void setyDiffNormalized(double yDiffNormalized) {
			this.yDiffNormalized = yDiffNormalized;
		}

	}
}
