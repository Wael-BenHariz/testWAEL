import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppComponent } from './app.component';
import { LivreComponent } from './books/livre/livre.component';
import { LeslivreComponent } from './books/leslivre/leslivre.component';

@NgModule({
  declarations: [
    AppComponent,
    LivreComponent,
    LeslivreComponent
  ],
  imports: [
    BrowserModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
