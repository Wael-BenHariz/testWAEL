import { Component, OnInit } from '@angular/core';
import { Livre } from 'src/app/model/livre';

@Component({
  selector: 'wael-leslivre',
  templateUrl: './leslivre.component.html',
  styleUrls: ['./leslivre.component.css']
})
export class LeslivreComponent implements OnInit {
  alllivre:Livre[]= [
    {title:"ASP.Net", cover:"assets/Books/asp.jpg", price:52.6,date:new Date(2018,9,7),reduction:20},
    {title:"Systeme", cover:"assets/Books/sys.jpg", price:25.3,date:new Date(2012,4,15),reduction:0},
    {title:"JAVA", cover:"assets/Books/java.jpg", price:48.2,date:new Date(2019,10,27),reduction:30}
  ]

  constructor() { }

  ngOnInit(): void {
  }

}
