package com.hhsdp;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * @author: remedios
 * @Description:
 * @create: 2022-11-02 12:06
 */
@Slf4j
public class SortTest {

    public static void main(String[] args) {

        int[] arr = new int[30];
        for (int i = 0; i < 30; i++) {
            arr[i] = (int)(Math.random() * 800);
        }
        int[] ints = {0,11, 45, 63, 82, 2, 5, 43, 14, 23, 9};
//        List<int[]> list = Arrays.asList(ints);
        List<int[]> list = Arrays.asList(arr);
//
//        test2 test2 = new test2();
////        test2.quickSort(arr,0,arr.length - 1);
        SortTest test = new SortTest();
//        test.quickSort(ints,0,ints.length - 1);
        test.quickSort(arr,0,arr.length - 1);

    }

    //有问题，应该是中间数的存放左右问题。待解决！！！！！！！！！11
    //快速排序(递归调用)
    public void quickSort(int[] arr,int start,int end){

        if(start < end){

            int left = start;//左指针
            int right = end;//右指针
            int temp = arr[start];//使用最左边的值作为基准值，从右边开始循环

            while(left < right){
                //如右边有比基准值小的数则停止循环
                while(left < right && arr[right] > temp){
                    right--;
                }

                //添加判断条件，防止right比left小(基准值就是最小值)
                if(left < right) {
                    //找到小的数，赋值给左边，左值针向前移动一位
                    arr[left] = arr[right];
                    left++;
                }

                //如左边有比基准值大的数则停止循环
                while(left < right && arr[left] <= temp){
                    left++;
                }

                //添加判断条件，防止right比left小(基准值就是最小值)
                if(left < right) {
                    //找到大的数，赋值给右边，右值针向前移动一位
                    arr[right] = arr[left];
                    right--;
                }
            }

            //第一轮排序完成,left == right，将temp值放入这个位置
            arr[left] = temp;
            //将数组劈两半分别进行递归调用
            quickSort(arr,start,left);
            quickSort(arr,left + 1,end);
        }
        System.out.println(Arrays.toString(arr));
    }


    //希尔排序-交换法
    public void shellSort1(int[] arr){
        //此循环改变gap步长
        for(int gap = arr.length/2;gap > 0;gap /= 2){
            //此循环改变数组长度
            for(int i = gap;i < arr.length;i++){
                //此循环位置交换
                for(int j = i - gap;j >= 0;j -= gap){
                    if(arr[j] > arr[j + gap]){
                        int temp = arr[j];
                        arr[j] = arr[j + gap];
                        arr[j + gap] = temp;
                    }
                }
            }
        }
        System.out.println(Arrays.toString(arr));
    }


    //希尔排序-移位法
    public void shellSort2(int[] arr){
        //此循环改变gap步长
        for(int gap = arr.length/2;gap > 0;gap /= 2){
            //此循环改变数组长度
            for(int i = gap;i < arr.length;i++){
                int j = i;//元素下标
                int temp = arr[j];//元素值
                if(arr[j] < arr[j -gap]){
                    while(j - gap >= 0 && temp < arr[j - gap]){
                        arr[j] = arr[j - gap];
                        j -= gap;//减去gap，gap位置向前移动i++
                    }
                    arr[j] = temp;
                }
            }
        }
        System.out.println(Arrays.toString(arr));

    }


    //选择排序(从无序表中找<最小值>插入到有序表中）
    public void selectSort(int[] arr){
        //定义最小值、下标、中间数
        int minValue,minIndex,temp;
        for(int i = 0;i < arr.length - 1;i++){
            minIndex = i;
            minValue = arr[i];
            for(int j = i + 1; j < arr.length;j++){
                //遍历数组寻找最小值
                if(minValue > arr[j]){
                    minValue = arr[j];
                    minIndex = j;
                }
            }
            //循环结束表示找到最小值，然后交换元素
            if(minIndex > 0){
                temp = arr[i];
                arr[i] = arr[minIndex];
                arr[minIndex] = temp;
            }
        }
        System.out.println(Arrays.toString(arr));
    }


    //插入排序(无序表到有序表) 注意定义插入值和下标
    public void insertSort(int[] arr){
        for(int i = 1;i < arr.length;i++){
            //先定义待插入数据和插入位置下标
            int insertValue = arr[i];
            int insertIndex = i - 1;
            //查找位置
            while(insertValue < arr[insertIndex] && insertIndex >= 0){
                arr[insertIndex + 1] = arr[insertIndex];
                insertIndex--;
            }
            //找到位置后插入
            arr[insertIndex + 1] = insertValue;
        }
        System.out.println(Arrays.toString(arr));
    }


    //冒泡排序，双重for循环，-1-i，交换
    public void bubbleSort(int[] arr){
        for(int i = 0;i < arr.length - 1;i++){
            for(int j = 0; j < arr.length - 1 - i;j++){
                if(arr[j] > arr[j + 1]){
                    int tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                }
            }
        }
        System.out.println(Arrays.toString(arr));
    }



}
