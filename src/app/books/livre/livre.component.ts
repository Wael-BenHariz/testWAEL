import { Component, Input, OnInit } from '@angular/core';
import { Livre } from 'src/app/model/livre';

@Component({
  selector: 'wael-livre',
  templateUrl: './livre.component.html',
  styleUrls: ['./livre.component.css']
})
export class LivreComponent implements OnInit {
  @Input[] liv:Livre[];
  @input[] indice:number;

  finalPrice(){

  }

  constructor() { }

  ngOnInit(): void {
  }

}
