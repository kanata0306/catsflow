package com.catsflow.catsflow.hello.Entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Transaction {
    private LocalDateTime date;
    private int amount;
    private String type;
    private String memo;
}